package br.com.fiap.techchallenge.oficina.os.external.config;

import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.saga.controllers.PosCommitSaga;
import br.com.fiap.techchallenge.oficina.os.saga.controllers.SagaController;
import br.com.fiap.techchallenge.oficina.os.saga.entities.PrazosSaga;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.InboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.OutboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.RastroMensagensGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.ProcessarEventoSagaUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.ReprocessarSagaUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.VerificarPrazosSagaUseCase;
import br.com.fiap.techchallenge.oficina.os.shared.usecases.ExecutorTransacional;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;

@ApplicationScoped
public class SagaBeans {

    /**
     * Prazos da saga (saga.md §6), por configuração: na demonstração são reduzidos para
     * minutos sem recompilar — {@code SAGA_PRAZO_APROVACAO=PT2M}, por exemplo.
     */
    @Produces
    @Singleton   // record é final: escopo normal exigiria proxy, que não pode estender classe final
    PrazosSaga prazosSaga(
            @ConfigProperty(name = "oficina.saga.prazo.resposta", defaultValue = "PT30S") Duration resposta,
            @ConfigProperty(name = "oficina.saga.prazo.aprovacao", defaultValue = "PT72H") Duration aprovacao,
            @ConfigProperty(name = "oficina.saga.prazo.pagamento", defaultValue = "PT35M") Duration pagamento,
            @ConfigProperty(name = "oficina.saga.max-tentativas", defaultValue = "3") int maxTentativas) {
        return new PrazosSaga(resposta, aprovacao, pagamento, maxTentativas);
    }

    /** Relógio do sistema. {@code @DefaultBean}: os testes de prazo trocam por um relógio fixo. */
    @Produces
    @Singleton
    @DefaultBean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Produces
    @ApplicationScoped
    SagaController sagaController(InboxGateway inbox, SagaGateway sagas, OrdemServicoGateway ordens,
                                  ClienteGateway clientes, OutboxGateway outbox, RastroMensagensGateway rastro,
                                  PrazosSaga prazos, Clock relogio, PosCommitSaga posCommit, ExecutorTransacional tx) {
        return new SagaController(
                new ProcessarEventoSagaUseCase(inbox, sagas, ordens, clientes, outbox, prazos, relogio),
                new VerificarPrazosSagaUseCase(sagas, ordens, outbox, prazos, relogio),
                new ReprocessarSagaUseCase(sagas, ordens, outbox, prazos, relogio),
                sagas, ordens, rastro, posCommit, tx);
    }
}
