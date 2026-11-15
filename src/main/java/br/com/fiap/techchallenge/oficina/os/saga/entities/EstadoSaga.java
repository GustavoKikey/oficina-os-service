package br.com.fiap.techchallenge.oficina.os.saga.entities;

/**
 * Estados da saga da OS ({@code docs/fase-4/saga.md} §3).
 *
 * <p>Os estados {@code *_SOLICITADO} e {@code COMPENSANDO} aguardam a <b>resposta de um
 * serviço</b> e têm timeout técnico com reenvio. {@code EM_DIAGNOSTICO},
 * {@code AGUARDANDO_APROVACAO} e {@code EM_REPARO} aguardam <b>pessoas</b>.
 */
public enum EstadoSaga {
    EXECUCAO_SOLICITADA(true),
    EM_DIAGNOSTICO(false),
    ORCAMENTO_SOLICITADO(true),
    AGUARDANDO_APROVACAO(false),
    PAGAMENTO_SOLICITADO(false),
    REPARO_SOLICITADO(true),
    EM_REPARO(false),
    COMPENSANDO(true),
    CONCLUIDA(false),
    COMPENSADA(false),
    FALHA(false);

    private final boolean aguardaRespostaDeServico;

    EstadoSaga(boolean aguardaRespostaDeServico) {
        this.aguardaRespostaDeServico = aguardaRespostaDeServico;
    }

    /** Silêncio do participante neste estado leva a reenvio e, esgotado, a FALHA. */
    public boolean aguardaRespostaDeServico() {
        return aguardaRespostaDeServico;
    }

    public boolean encerrada() {
        return this == CONCLUIDA || this == COMPENSADA;
    }
}
