package br.com.fiap.techchallenge.oficina.os.saga.controllers;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.saga.dtos.SagaResponse;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.RastroMensagensGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.EfeitoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.ProcessarEventoSagaUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.ReprocessarSagaUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.VerificarPrazosSagaUseCase;
import br.com.fiap.techchallenge.oficina.os.shared.usecases.ExecutorTransacional;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface Adapter da saga para os gatilhos que não são decisões de usuário: eventos
 * dos participantes (consumidor RabbitMQ), prazos (agendador) e operação (admin).
 */
public class SagaController {

    private final ProcessarEventoSagaUseCase processar;
    private final VerificarPrazosSagaUseCase prazos;
    private final ReprocessarSagaUseCase reprocessar;
    private final SagaGateway sagas;
    private final OrdemServicoGateway ordens;
    private final RastroMensagensGateway rastro;
    private final PosCommitSaga posCommit;
    private final ExecutorTransacional tx;

    public SagaController(ProcessarEventoSagaUseCase processar, VerificarPrazosSagaUseCase prazos,
                          ReprocessarSagaUseCase reprocessar, SagaGateway sagas, OrdemServicoGateway ordens,
                          RastroMensagensGateway rastro, PosCommitSaga posCommit, ExecutorTransacional tx) {
        this.processar = processar;
        this.prazos = prazos;
        this.reprocessar = reprocessar;
        this.sagas = sagas;
        this.ordens = ordens;
        this.rastro = rastro;
        this.posCommit = posCommit;
        this.tx = tx;
    }

    /** Um evento, uma transação (inbox + saga + OS + outbox), depois os efeitos externos. */
    public EfeitoSaga processarEvento(UUID messageId, UUID osId, EventoSaga evento) {
        EfeitoSaga efeito = tx.emTransacao(() -> processar.executar(
                new ProcessarEventoSagaUseCase.Input(messageId, OrdemServicoId.de(osId), evento)));
        posCommit.aplicar(efeito, evento instanceof EventoSaga.PagamentoSolicitado);
        return efeito;
    }

    /** @return quantas sagas tiveram o prazo tratado nesta passada */
    public int verificarPrazos() {
        int tratadas = 0;
        for (OrdemServicoId osId : tx.emTransacao(prazos::vencidas)) {
            Optional<EfeitoSaga> efeito = tx.emTransacao(() -> prazos.verificar(osId));
            if (efeito.isPresent()) {
                posCommit.aplicar(efeito.get(), false);
                tratadas++;
            }
        }
        return tratadas;
    }

    public SagaResponse consultar(UUID osId) {
        return tx.emTransacao(() -> apresentar(OrdemServicoId.de(osId)));
    }

    public SagaResponse reprocessar(UUID osId) {
        tx.emTransacao(() -> reprocessar.executar(OrdemServicoId.de(osId)));
        return consultar(osId);
    }

    private SagaResponse apresentar(OrdemServicoId osId) {
        SagaOrdemServico saga = sagas.buscarPorOs(osId).orElseThrow(() -> new OrdemServicoNaoEncontradaException(osId));
        OrdemServico os = ordens.buscarPorId(osId).orElseThrow(() -> new OrdemServicoNaoEncontradaException(osId));
        return new SagaResponse(
                osId.valor(),
                saga.estado().name(),
                saga.estadoAntesDaFalha() == null ? null : saga.estadoAntesDaFalha().name(),
                os.status().name(),
                saga.prazo(),
                saga.tentativas(),
                saga.comandosPendentes().stream()
                        .map(c -> new SagaResponse.ComandoView(c.tipo(), c.destino().routingKey()))
                        .toList(),
                saga.motivoFalha(),
                saga.iniciadaEm(),
                saga.atualizadaEm(),
                rastro.daOs(osId).stream()
                        .map(m -> new SagaResponse.MensagemView(m.direcao().name(), m.tipo(), m.messageId(), m.em(),
                                m.publicadaEm()))
                        .toList());
    }
}
