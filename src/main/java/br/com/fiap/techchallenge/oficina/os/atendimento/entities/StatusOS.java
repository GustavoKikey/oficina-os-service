package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

/**
 * Status do ciclo de vida de uma Ordem de Serviço — o que o cliente vê.
 *
 * <p>Na fase 4 quem move a OS é a saga ({@code docs/fase-4/saga.md} §3), a partir dos
 * eventos de Execução e Billing:
 * <pre>
 *   (abertura)            → RECEBIDA
 *   RECEBIDA              → EM_DIAGNOSTICO        (DiagnosticoIniciado)
 *   EM_DIAGNOSTICO        → AGUARDANDO_APROVACAO  (OrcamentoGerado)
 *   AGUARDANDO_APROVACAO  → AGUARDANDO_PAGAMENTO  (cliente aprova)     — novo na fase 4
 *   AGUARDANDO_PAGAMENTO  → EM_EXECUCAO           (PagamentoConfirmado)
 *   EM_EXECUCAO           → FINALIZADA            (ReparoFinalizado)
 *   FINALIZADA            → ENTREGUE              (atendente)
 *   qualquer não encerrado, exceto FINALIZADA → CANCELADA (compensação)
 * </pre>
 *
 * <p>Cada status carrega a descrição exibida ao cliente e a prioridade da listagem
 * operacional (menor = mais urgente). Encerrados ficam fora da listagem padrão —
 * exclusão lógica, nunca física.
 */
public enum StatusOS {
    RECEBIDA("Recebida", 5, true),
    EM_DIAGNOSTICO("Diagnóstico", 4, true),
    AGUARDANDO_APROVACAO("Aguardando Aprovação", 3, true),
    AGUARDANDO_PAGAMENTO("Aguardando Pagamento", 2, true),
    EM_EXECUCAO("Execução", 1, true),
    FINALIZADA("Finalizada", 9, false),
    ENTREGUE("Entregue", 9, false),
    CANCELADA("Cancelada", 9, false);

    private final String descricao;
    private final int prioridadeListagem;
    private final boolean visivelNaListagemPadrao;

    StatusOS(String descricao, int prioridadeListagem, boolean visivelNaListagemPadrao) {
        this.descricao = descricao;
        this.prioridadeListagem = prioridadeListagem;
        this.visivelNaListagemPadrao = visivelNaListagemPadrao;
    }

    /** Descrição amigável exigida pela consulta de status (ex.: "Aguardando Aprovação"). */
    public String descricao() {
        return descricao;
    }

    /** Prioridade na listagem: Execução &gt; Pagamento &gt; Aprovação &gt; Diagnóstico &gt; Recebida. */
    public int prioridadeListagem() {
        return prioridadeListagem;
    }

    /** Encerradas (finalizada/entregue/cancelada) saem da listagem padrão (exclusão lógica). */
    public boolean visivelNaListagemPadrao() {
        return visivelNaListagemPadrao;
    }

    /** A OS ainda pode ser cancelada por compensação: não foi finalizada nem encerrada. */
    public boolean cancelavel() {
        return this != FINALIZADA && this != ENTREGUE && this != CANCELADA;
    }
}
