package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

/**
 * Por que uma OS foi cancelada. Os valores são os mesmos do campo {@code motivo} dos
 * comandos de compensação ({@code docs/fase-4/contratos-mensagens.md} §3).
 */
public enum MotivoCancelamento {
    ORCAMENTO_NAO_GERADO("O orçamento não pôde ser gerado"),
    ORCAMENTO_RECUSADO("Orçamento recusado"),
    PRAZO_APROVACAO_VENCIDO("Prazo para aprovar o orçamento venceu"),
    PAGAMENTO_RECUSADO("Pagamento recusado"),
    PAGAMENTO_EXPIRADO("Prazo para pagamento venceu"),
    REPARO_NAO_LIBERADO("Reparo não pôde ser iniciado — pagamento estornado");

    private final String descricao;

    MotivoCancelamento(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}
