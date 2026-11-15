package br.com.fiap.techchallenge.oficina.os.saga.entities;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ItemOS;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Eventos dos participantes que a saga sabe tratar ({@code contratos-mensagens.md} §4),
 * já traduzidos do JSON para tipos do domínio pela camada de mensageria.
 *
 * <p>Interface selada: o {@code switch} da saga é verificado pelo compilador — um
 * evento novo que ninguém trata não compila.
 */
public sealed interface EventoSaga {

    /** Nome do evento como no contrato (ex.: {@code "OrcamentoGerado"}). */
    default String tipo() {
        return getClass().getSimpleName();
    }

    // ----- Execução -----
    record OrdemExecucaoAberta() implements EventoSaga {}
    record DiagnosticoIniciado(OffsetDateTime iniciadoEm) implements EventoSaga {}
    record DiagnosticoConcluido(OffsetDateTime iniciadoEm, OffsetDateTime concluidoEm,
                                List<ItemOS> itens) implements EventoSaga {
        public DiagnosticoConcluido {
            itens = List.copyOf(itens);
        }
    }
    record ReparoLiberado(OffsetDateTime liberadoEm) implements EventoSaga {}
    record ReparoNaoLiberado(String motivo) implements EventoSaga {}
    record ReparoIniciado() implements EventoSaga {}
    record ReparoFinalizado(OffsetDateTime finalizadoEm) implements EventoSaga {}
    record OrdemExecucaoCancelada() implements EventoSaga {}

    // ----- Billing -----
    record OrcamentoGerado(UUID orcamentoId, Dinheiro valorTotal, OffsetDateTime geradoEm) implements EventoSaga {}
    record OrcamentoNaoGerado(String motivo) implements EventoSaga {}
    record PagamentoSolicitado(String linkPagamento, OffsetDateTime expiraEm) implements EventoSaga {}
    record PagamentoConfirmado(OffsetDateTime confirmadoEm) implements EventoSaga {}
    record PagamentoRecusado(String motivo) implements EventoSaga {}
    record PagamentoExpirado() implements EventoSaga {}
    record OrcamentoCancelado() implements EventoSaga {}
    record PagamentoEstornado() implements EventoSaga {}
    record EstornoFalhou(String erro) implements EventoSaga {}
}
