package br.com.fiap.techchallenge.oficina.os.saga.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ResultadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;

/**
 * O que um passo da saga causou, já persistido. Serve ao controller para o que acontece
 * <b>depois do commit</b>: notificar o cliente e registrar métricas — efeitos que não
 * têm rollback e por isso não entram na transação.
 *
 * @param os        nulo quando a mensagem foi ignorada antes de carregar a OS
 * @param duplicada mensagem já processada (inbox) — nenhum efeito
 */
public record EfeitoSaga(OrdemServico os, SagaOrdemServico saga, ResultadoSaga resultado,
                         StatusOS statusAntes, EstadoSaga estadoAntes, boolean duplicada) {

    static EfeitoSaga jaProcessada() {
        return new EfeitoSaga(null, null, null, null, null, true);
    }

    static EfeitoSaga semSaga(ResultadoSaga resultado) {
        return new EfeitoSaga(null, null, resultado, null, null, false);
    }

    public boolean statusMudou() {
        return os != null && statusAntes != null && os.status() != statusAntes;
    }

    public boolean entrouEm(EstadoSaga estado) {
        return saga != null && saga.estado() == estado && estadoAntes != estado;
    }
}
