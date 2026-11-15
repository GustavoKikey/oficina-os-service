-- =====================================================================
-- OS Service — schema inicial (fase 4)
-- ---------------------------------------------------------------------
-- Banco PRÓPRIO do OS Service. Nasce limpo na fase 4: clientes, veículos e
-- usuários vêm do monolito da fase 3 (V2, V5, V7) sem mudança de regra; a
-- ordem de serviço perde itens e reservas (agora na Execução) e o orçamento
-- completo (agora no Billing), guardando só cópias para exibir ao cliente.
--
-- Nenhuma FK aponta para fora deste banco: orcamento_id e itens referenciam
-- dados de outros serviços apenas por UUID (docs/fase-4/arquitetura.md §5).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Atendimento
-- ---------------------------------------------------------------------
CREATE TABLE clientes (
    id          UUID         PRIMARY KEY,
    nome        VARCHAR(150) NOT NULL,
    documento   VARCHAR(14)  NOT NULL UNIQUE,
    email       VARCHAR(150),
    telefone    VARCHAR(30),
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON COLUMN clientes.ativo IS
    'Cliente habilitado a autenticar e abrir OS. FALSE faz a Lambda negar o token.';

-- Busca da Lambda (GET /internal/clientes?documento=) só olha clientes ativos.
CREATE INDEX idx_clientes_documento_ativo ON clientes (documento) WHERE ativo;

CREATE TABLE veiculos (
    id          UUID         PRIMARY KEY,
    placa       VARCHAR(7)   NOT NULL UNIQUE,
    marca       VARCHAR(80)  NOT NULL,
    modelo      VARCHAR(120) NOT NULL,
    ano         INTEGER      NOT NULL,
    cliente_id  UUID         NOT NULL REFERENCES clientes(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_veiculos_cliente_id ON veiculos (cliente_id);

CREATE TABLE ordens_servico (
    id                        UUID           PRIMARY KEY,
    cliente_id                UUID           NOT NULL REFERENCES clientes(id),
    veiculo_id                UUID           NOT NULL REFERENCES veiculos(id),
    status                    VARCHAR(32)    NOT NULL CHECK (status IN
        ('RECEBIDA','EM_DIAGNOSTICO','AGUARDANDO_APROVACAO','AGUARDANDO_PAGAMENTO',
         'EM_EXECUCAO','FINALIZADA','ENTREGUE','CANCELADA')),
    relato_cliente            VARCHAR(500),

    -- Resumo do orçamento (o orçamento completo vive no Billing).
    orcamento_id              UUID,
    valor_total               NUMERIC(12, 2) CHECK (valor_total >= 0),
    orcamento_gerado_em       TIMESTAMPTZ,
    orcamento_aprovado_em     TIMESTAMPTZ,
    orcamento_aprovado_por    VARCHAR(16)    CHECK (orcamento_aprovado_por IN ('CLIENTE','ATENDENTE')),

    -- Pagamento (link do Mercado Pago e confirmação, recebidos do Billing).
    link_pagamento            VARCHAR(500),
    pagamento_expira_em       TIMESTAMPTZ,
    pagamento_confirmado_em   TIMESTAMPTZ,

    motivo_cancelamento       VARCHAR(32),

    -- Marcos temporais: base das métricas de tempo por fase (fase 3).
    criada_em                 TIMESTAMPTZ    NOT NULL,
    diagnostico_iniciado_em   TIMESTAMPTZ,
    execucao_iniciada_em      TIMESTAMPTZ,
    finalizada_em             TIMESTAMPTZ,
    entregue_em               TIMESTAMPTZ,
    cancelada_em              TIMESTAMPTZ
);

CREATE INDEX idx_ordens_servico_status     ON ordens_servico (status);
CREATE INDEX idx_ordens_servico_cliente_id ON ordens_servico (cliente_id);
CREATE INDEX idx_ordens_servico_veiculo_id ON ordens_servico (veiculo_id);

-- Cópia dos itens do diagnóstico (fonte: evento DiagnosticoConcluido da Execução).
-- Só leitura para este serviço: existe para o cliente ver o que está aprovando.
CREATE TABLE os_itens (
    id              UUID           PRIMARY KEY,
    os_id           UUID           NOT NULL REFERENCES ordens_servico(id) ON DELETE CASCADE,
    posicao         INTEGER        NOT NULL,
    tipo            VARCHAR(8)     NOT NULL CHECK (tipo IN ('SERVICO','PECA')),
    referencia_id   UUID           NOT NULL,
    descricao       VARCHAR(200)   NOT NULL,
    quantidade      INTEGER        NOT NULL CHECK (quantidade > 0),
    valor_unitario  NUMERIC(12, 2) NOT NULL CHECK (valor_unitario >= 0),
    UNIQUE (os_id, posicao)
);

-- Histórico de status ("consulta de status e histórico" — enunciado, OS Service).
CREATE TABLE os_historico (
    id          BIGSERIAL    PRIMARY KEY,
    os_id       UUID         NOT NULL REFERENCES ordens_servico(id) ON DELETE CASCADE,
    de          VARCHAR(32),
    para        VARCHAR(32)  NOT NULL,
    em          TIMESTAMPTZ  NOT NULL,
    motivo      VARCHAR(120)
);

CREATE INDEX idx_os_historico_os_id ON os_historico (os_id, id);

-- ---------------------------------------------------------------------
-- Segurança
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
    id          UUID            PRIMARY KEY,
    username    VARCHAR(80)     NOT NULL UNIQUE,
    senha_hash  VARCHAR(100)    NOT NULL,
    role        VARCHAR(16)     NOT NULL CHECK (role IN ('ATENDENTE','MECANICO','ADMINISTRADOR')),
    ativo       BOOLEAN         NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- Saga (docs/fase-4/saga.md)
-- ---------------------------------------------------------------------
CREATE TABLE sagas (
    os_id                 UUID         PRIMARY KEY REFERENCES ordens_servico(id),
    estado                VARCHAR(32)  NOT NULL,
    estado_antes_falha    VARCHAR(32),
    prazo                 TIMESTAMPTZ,
    tentativas            INTEGER      NOT NULL DEFAULT 0,
    -- Comandos cuja resposta a saga aguarda (reenviados no timeout). JSON.
    comandos_pendentes    TEXT         NOT NULL DEFAULT '[]',
    motivo_falha          VARCHAR(300),
    iniciada_em           TIMESTAMPTZ  NOT NULL,
    atualizada_em         TIMESTAMPTZ  NOT NULL,
    versao                BIGINT       NOT NULL DEFAULT 0
);

-- O verificador de prazos busca só o que pode vencer.
CREATE INDEX idx_sagas_prazo ON sagas (prazo) WHERE prazo IS NOT NULL;

-- ---------------------------------------------------------------------
-- Mensageria confiável (ADR 010)
-- ---------------------------------------------------------------------
-- Outbox: gravado na MESMA transação do estado; publicado depois pelo relay.
CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    message_id      UUID         NOT NULL UNIQUE,
    tipo            VARCHAR(64)  NOT NULL,
    routing_key     VARCHAR(64)  NOT NULL,
    correlation_id  UUID         NOT NULL,
    envelope        TEXT         NOT NULL,
    -- traceparent/tracestate capturados na transação: o relay roda em outra
    -- thread e, sem isso, o trace se partiria justamente na fila.
    headers         TEXT         NOT NULL DEFAULT '{}',
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_em    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pendentes ON outbox (id) WHERE publicado_em IS NULL;
CREATE INDEX idx_outbox_correlation ON outbox (correlation_id);

-- Inbox: deduplicação de eventos pelo messageId, na mesma transação do efeito.
CREATE TABLE mensagens_processadas (
    message_id      UUID         PRIMARY KEY,
    tipo            VARCHAR(64)  NOT NULL,
    correlation_id  UUID         NOT NULL,
    processada_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_mensagens_processadas_correlation ON mensagens_processadas (correlation_id);
