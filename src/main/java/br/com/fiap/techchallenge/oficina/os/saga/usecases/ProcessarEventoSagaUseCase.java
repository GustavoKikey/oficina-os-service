package br.com.fiap.techchallenge.oficina.os.saga.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.PrazosSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ResultadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.InboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.OutboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Aplica um evento de participante à saga. Deve rodar numa única transação: inbox,
 * saga, OS e outbox são gravados juntos ou nada é gravado (ADR 010).
 */
public class ProcessarEventoSagaUseCase {

    private final InboxGateway inbox;
    private final SagaGateway sagas;
    private final OrdemServicoGateway ordens;
    private final ClienteGateway clientes;
    private final OutboxGateway outbox;
    private final PrazosSaga prazos;
    private final Clock relogio;

    public ProcessarEventoSagaUseCase(InboxGateway inbox, SagaGateway sagas, OrdemServicoGateway ordens,
                                      ClienteGateway clientes, OutboxGateway outbox, PrazosSaga prazos,
                                      Clock relogio) {
        this.inbox = inbox;
        this.sagas = sagas;
        this.ordens = ordens;
        this.clientes = clientes;
        this.outbox = outbox;
        this.prazos = prazos;
        this.relogio = relogio;
    }

    public record Input(UUID messageId, OrdemServicoId osId, EventoSaga evento) {}

    public EfeitoSaga executar(Input input) {
        if (!inbox.registrarSeNova(input.messageId(), input.evento().tipo(), input.osId())) {
            return EfeitoSaga.jaProcessada();
        }

        Optional<SagaOrdemServico> encontrada = sagas.buscarPorOsComLock(input.osId());
        if (encontrada.isEmpty()) {
            // Evento de uma OS que este serviço não conhece (ambiente recriado, mensagem
            // antiga na fila). Não há o que fazer — reprocessar não mudaria isso.
            return EfeitoSaga.semSaga(new ResultadoSaga(false, java.util.List.of(), false,
                    "nenhuma saga para a OS " + input.osId()));
        }
        SagaOrdemServico saga = encontrada.get();
        OrdemServico os = ordens.buscarPorId(input.osId())
                .orElseThrow(() -> new OrdemServicoNaoEncontradaException(input.osId()));
        // FK garante o cliente; se faltar, é defeito de dados — falha técnica, vai à DLQ.
        Cliente cliente = clientes.buscarPorId(os.clienteId())
                .orElseThrow(() -> new IllegalStateException(
                        "OS " + os.id() + " aponta para cliente inexistente " + os.clienteId()));

        StatusOS statusAntes = os.status();
        EstadoSaga estadoAntes = saga.estado();
        OffsetDateTime agora = OffsetDateTime.now(relogio);

        ResultadoSaga resultado = saga.aoReceber(input.evento(), os, cliente, prazos, agora);
        if (resultado.aplicado()) {
            ordens.salvar(os);
            sagas.salvar(saga);
            outbox.enfileirar(resultado.comandos(), os.id(), input.messageId());
        }
        return new EfeitoSaga(os, saga, resultado, statusAntes, estadoAntes, false);
    }
}
