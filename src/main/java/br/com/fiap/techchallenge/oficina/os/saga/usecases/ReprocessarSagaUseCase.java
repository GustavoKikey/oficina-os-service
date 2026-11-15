package br.com.fiap.techchallenge.oficina.os.saga.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.PrazosSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ResultadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.OutboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Operação manual (saga.md §8): depois de resolvida a causa de uma {@code FALHA}, o
 * administrador reenvia o comando do passo em que a saga parou.
 */
public class ReprocessarSagaUseCase {

    private final SagaGateway sagas;
    private final OrdemServicoGateway ordens;
    private final OutboxGateway outbox;
    private final PrazosSaga prazos;
    private final Clock relogio;

    public ReprocessarSagaUseCase(SagaGateway sagas, OrdemServicoGateway ordens, OutboxGateway outbox,
                                  PrazosSaga prazos, Clock relogio) {
        this.sagas = sagas;
        this.ordens = ordens;
        this.outbox = outbox;
        this.prazos = prazos;
        this.relogio = relogio;
    }

    public EfeitoSaga executar(OrdemServicoId osId) {
        SagaOrdemServico saga = sagas.buscarPorOsComLock(osId)
                .orElseThrow(() -> new OrdemServicoNaoEncontradaException(osId));
        OrdemServico os = ordens.buscarPorId(osId).orElseThrow(() -> new OrdemServicoNaoEncontradaException(osId));
        EstadoSaga estadoAntes = saga.estado();

        ResultadoSaga resultado = saga.reprocessar(prazos, OffsetDateTime.now(relogio));
        sagas.salvar(saga);
        outbox.enfileirar(resultado.comandos(), osId, null);
        return new EfeitoSaga(os, saga, resultado, os.status(), estadoAntes, false);
    }
}
