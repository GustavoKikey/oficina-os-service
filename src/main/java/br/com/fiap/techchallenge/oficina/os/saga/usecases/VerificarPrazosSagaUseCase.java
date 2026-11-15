package br.com.fiap.techchallenge.oficina.os.saga.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.PrazosSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ResultadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.OutboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Prazos vencidos (saga.md §6). Dois passos, para que cada saga rode na <b>sua</b>
 * transação — uma saga com problema não desfaz o tratamento das outras:
 * <ol>
 *   <li>{@link #vencidas()} lista as OS candidatas, sem trava;</li>
 *   <li>{@link #verificar(OrdemServicoId)} trava a saga e confere o prazo de novo — se
 *       outra réplica já tratou, o prazo mudou e nada acontece.</li>
 * </ol>
 */
public class VerificarPrazosSagaUseCase {

    private static final int LOTE = 50;

    private final SagaGateway sagas;
    private final OrdemServicoGateway ordens;
    private final OutboxGateway outbox;
    private final PrazosSaga prazos;
    private final Clock relogio;

    public VerificarPrazosSagaUseCase(SagaGateway sagas, OrdemServicoGateway ordens, OutboxGateway outbox,
                                      PrazosSaga prazos, Clock relogio) {
        this.sagas = sagas;
        this.ordens = ordens;
        this.outbox = outbox;
        this.prazos = prazos;
        this.relogio = relogio;
    }

    public List<OrdemServicoId> vencidas() {
        return sagas.listarComPrazoVencido(OffsetDateTime.now(relogio), LOTE);
    }

    public Optional<EfeitoSaga> verificar(OrdemServicoId osId) {
        Optional<SagaOrdemServico> encontrada = sagas.buscarPorOsComLock(osId);
        if (encontrada.isEmpty()) {
            return Optional.empty();
        }
        SagaOrdemServico saga = encontrada.get();
        OrdemServico os = ordens.buscarPorId(osId).orElseThrow(() -> new OrdemServicoNaoEncontradaException(osId));

        StatusOS statusAntes = os.status();
        EstadoSaga estadoAntes = saga.estado();
        ResultadoSaga resultado = saga.verificarPrazo(os, prazos, OffsetDateTime.now(relogio));
        if (!resultado.aplicado()) {
            return Optional.empty();
        }
        ordens.salvar(os);
        sagas.salvar(saga);
        outbox.enfileirar(resultado.comandos(), osId, null);
        return Optional.of(new EfeitoSaga(os, saga, resultado, statusAntes, estadoAntes, false));
    }
}
