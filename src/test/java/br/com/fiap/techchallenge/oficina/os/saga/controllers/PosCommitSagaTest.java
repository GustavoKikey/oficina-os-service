package br.com.fiap.techchallenge.oficina.os.saga.controllers;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.MotivoCancelamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.NotificarStatusOSUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ResultadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.EfeitoSaga;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.os.shared.usecases.ExecutorTransacional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.AGORA;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.alice;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.argoDe;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.itensDiagnostico;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.osAberta;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PosCommitSagaTest {

    private NotificarStatusOSUseCase notificar;
    private MetricasGateway metricas;
    private PosCommitSaga posCommit;
    private OrdemServico os;

    @BeforeEach
    void preparar() {
        notificar = mock(NotificarStatusOSUseCase.class);
        metricas = mock(MetricasGateway.class);
        posCommit = new PosCommitSaga(notificar, metricas, new ExecutorTransacional() {
            @Override
            public <T> T emTransacao(java.util.function.Supplier<T> acao) {
                return acao.get();
            }
        });
        Cliente c = alice();
        os = osAberta(c, argoDe(c));
    }

    private static SagaOrdemServico saga(OrdemServico os, EstadoSaga estado) {
        return SagaOrdemServico.reconstituir(os.id(), estado, null, null, 0, List.of(), null, AGORA, AGORA, 0);
    }

    private static ResultadoSaga aplicado(boolean falhou) {
        return new ResultadoSaga(true, List.of(), falhou, "teste");
    }

    @Test
    void mudancaDeStatusNotificaEMedeAFase() {
        os.iniciarDiagnostico(AGORA);
        os.concluirDiagnostico(itensDiagnostico(), AGORA, AGORA);
        os.registrarOrcamento(ResumoOrcamento.gerado(UUID.randomUUID(), Dinheiro.de("1.00"), AGORA));
        os.aprovarOrcamento(ResumoOrcamento.AprovadoPor.CLIENTE, AGORA);
        os.confirmarPagamento(AGORA.plusHours(2));

        posCommit.aplicar(new EfeitoSaga(os, saga(os, EstadoSaga.REPARO_SOLICITADO), aplicado(false),
                StatusOS.AGUARDANDO_PAGAMENTO, EstadoSaga.PAGAMENTO_SOLICITADO, false), false);

        verify(notificar).executar(os);
        verify(metricas).faseConcluida("Diagnóstico", Duration.ofHours(2));
    }

    @Test
    void linkDePagamentoAvisaMesmoSemMudarStatus() {
        posCommit.aplicar(new EfeitoSaga(os, saga(os, EstadoSaga.PAGAMENTO_SOLICITADO), aplicado(false),
                os.status(), EstadoSaga.PAGAMENTO_SOLICITADO, false), true);

        verify(notificar).executar(os);
        verify(metricas, never()).faseConcluida(any(), any());
    }

    @Test
    void conclusaoCompensacaoEFalhaViramMetricas() {
        posCommit.aplicar(new EfeitoSaga(os, saga(os, EstadoSaga.CONCLUIDA), aplicado(false),
                os.status(), EstadoSaga.EM_REPARO, false), false);
        verify(metricas).sagaConcluida();

        os.cancelar(MotivoCancelamento.ORCAMENTO_RECUSADO, AGORA);
        posCommit.aplicar(new EfeitoSaga(os, saga(os, EstadoSaga.COMPENSANDO), aplicado(false),
                StatusOS.AGUARDANDO_APROVACAO, EstadoSaga.AGUARDANDO_APROVACAO, false), false);
        verify(metricas).sagaCompensada("ORCAMENTO_RECUSADO");

        posCommit.aplicar(new EfeitoSaga(os, saga(os, EstadoSaga.FALHA), aplicado(true),
                os.status(), EstadoSaga.EXECUCAO_SOLICITADA, false), false);
        verify(metricas).sagaFalhou(eq("EXECUCAO_SOLICITADA"));
    }

    @Test
    void duplicadaOuIgnoradaNaoTemEfeitoExterno() {
        posCommit.aplicar(new EfeitoSaga(null, null, null, null, null, true), true);
        posCommit.aplicar(new EfeitoSaga(os, saga(os, EstadoSaga.EM_DIAGNOSTICO),
                new ResultadoSaga(false, List.of(), false, "ignorado"), os.status(), EstadoSaga.EM_DIAGNOSTICO,
                false), true);

        verifyNoInteractions(notificar, metricas);
    }

    @Test
    void falhaAoNotificarNaoPropagaENemImpedeAsMetricas() {
        org.mockito.Mockito.doThrow(new IllegalStateException("sem request context"))
                .when(notificar).executar(any());

        posCommit.aplicar(new EfeitoSaga(os, saga(os, EstadoSaga.CONCLUIDA), aplicado(false),
                StatusOS.EM_EXECUCAO, EstadoSaga.EM_REPARO, false), false);

        verify(metricas).falhaIntegracao("notificacao");
        verify(metricas).sagaConcluida();
    }

    @Test
    void fasesDeExecucaoEFinalizacao() {
        os.iniciarDiagnostico(AGORA);
        os.concluirDiagnostico(itensDiagnostico(), AGORA, AGORA);
        os.registrarOrcamento(ResumoOrcamento.gerado(UUID.randomUUID(), Dinheiro.de("1.00"), AGORA));
        os.aprovarOrcamento(ResumoOrcamento.AprovadoPor.CLIENTE, AGORA);
        os.confirmarPagamento(AGORA);
        os.finalizar(AGORA.plusHours(3));
        posCommit.registrarFaseConcluida(os);
        verify(metricas).faseConcluida("Execução", Duration.ofHours(3));

        os.entregar(AGORA.plusHours(4));
        posCommit.registrarFaseConcluida(os);
        verify(metricas).faseConcluida("Finalizada", Duration.ofHours(1));
    }
}
