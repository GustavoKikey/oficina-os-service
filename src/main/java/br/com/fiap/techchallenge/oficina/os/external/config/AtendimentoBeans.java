package br.com.fiap.techchallenge.oficina.os.external.config;

import br.com.fiap.techchallenge.oficina.os.atendimento.controllers.ClienteController;
import br.com.fiap.techchallenge.oficina.os.atendimento.controllers.OrdemServicoController;
import br.com.fiap.techchallenge.oficina.os.atendimento.controllers.VeiculoController;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.NotificacaoGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.VeiculoGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.AbrirOrdemServicoUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.EntregarVeiculoUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.NotificarStatusOSUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.controllers.PosCommitSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.PrazosSaga;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.OutboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.DecidirOrcamentoUseCase;
import br.com.fiap.techchallenge.oficina.os.shared.usecases.ExecutorTransacional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/**
 * Composição dos controllers de atendimento. Os use cases são classes Java puras —
 * quem os monta com os adapters é este arquivo, na borda (Clean Architecture).
 */
@ApplicationScoped
public class AtendimentoBeans {

    @Produces
    @ApplicationScoped
    ClienteController clienteController(ClienteGateway clienteGateway, ExecutorTransacional tx) {
        return new ClienteController(clienteGateway, tx);
    }

    @Produces
    @ApplicationScoped
    VeiculoController veiculoController(VeiculoGateway veiculoGateway, ClienteGateway clienteGateway,
                                        ExecutorTransacional tx) {
        return new VeiculoController(veiculoGateway, clienteGateway, tx);
    }

    @Produces
    @ApplicationScoped
    NotificarStatusOSUseCase notificarStatus(ClienteGateway clientes, NotificacaoGateway notificacao) {
        return new NotificarStatusOSUseCase(clientes, notificacao);
    }

    @Produces
    @ApplicationScoped
    PosCommitSaga posCommitSaga(NotificarStatusOSUseCase notificar, MetricasGateway metricas,
                                ExecutorTransacional tx) {
        return new PosCommitSaga(notificar, metricas, tx);
    }

    @Produces
    @ApplicationScoped
    OrdemServicoController ordemServicoController(OrdemServicoGateway ordens, ClienteGateway clientes,
                                                  VeiculoGateway veiculos, SagaGateway sagas, OutboxGateway outbox,
                                                  PrazosSaga prazos, Clock relogio,
                                                  NotificarStatusOSUseCase notificar, PosCommitSaga posCommit,
                                                  ExecutorTransacional tx, MetricasGateway metricas) {
        return new OrdemServicoController(
                new AbrirOrdemServicoUseCase(ordens, clientes, veiculos, sagas, outbox, prazos, relogio),
                ordens,
                new DecidirOrcamentoUseCase(sagas, ordens, outbox, prazos, relogio),
                new EntregarVeiculoUseCase(ordens, relogio),
                notificar, posCommit, tx, metricas);
    }
}
