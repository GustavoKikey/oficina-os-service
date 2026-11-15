package br.com.fiap.techchallenge.oficina.os.saga.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.AcessoNegadoAOrdemServicoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.TransicaoStatus;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Veiculo;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.AbrirOrdemServicoUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.ConsultarHistoricoOSUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.EntregarVeiculoUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.entities.TransicaoSagaInvalidaException;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;
import br.com.fiap.techchallenge.oficina.os.suporte.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.AGORA;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.PRAZOS;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.alice;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.argoDe;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.bruno;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.itensDiagnostico;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Use cases da saga e de atendimento contra gateways em memória: provam que cada
 * passo grava OS, saga e outbox juntos, e que inbox e posse da OS são respeitados.
 */
class SagaUseCasesTest {

    private final Fakes.Ordens ordens = new Fakes.Ordens();
    private final Fakes.Clientes clientes = new Fakes.Clientes();
    private final Fakes.Veiculos veiculos = new Fakes.Veiculos();
    private final Fakes.Sagas sagas = new Fakes.Sagas();
    private final Fakes.Outbox outbox = new Fakes.Outbox();
    private final Fakes.Inbox inbox = new Fakes.Inbox();
    private final RelogioAjustavel relogio = new RelogioAjustavel();

    private Cliente alice;
    private Veiculo argo;

    /** Relógio que o teste avança — é o que permite testar prazos sem dormir. */
    static final class RelogioAjustavel extends Clock {
        private Instant agora = AGORA.toInstant();

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    @BeforeEach
    void preparar() {
        alice = alice();
        argo = argoDe(alice);
        clientes.com(alice);
        veiculos.com(argo);
    }

    private AbrirOrdemServicoUseCase abrir() {
        return new AbrirOrdemServicoUseCase(ordens, clientes, veiculos, sagas, outbox, PRAZOS, relogio);
    }

    private ProcessarEventoSagaUseCase processar() {
        return new ProcessarEventoSagaUseCase(inbox, sagas, ordens, clientes, outbox, PRAZOS, relogio);
    }

    private DecidirOrcamentoUseCase decidir() {
        return new DecidirOrcamentoUseCase(sagas, ordens, outbox, PRAZOS, relogio);
    }

    private OrdemServico abrirOsDaAlice() {
        return abrir().executar(new AbrirOrdemServicoUseCase.Input(
                new AbrirOrdemServicoUseCase.DadosCliente("529.982.247-25", null, null, null),
                new AbrirOrdemServicoUseCase.DadosVeiculo("ABC1D23", null, null, null),
                "Barulho"));
    }

    private EfeitoSaga evento(OrdemServicoId os, EventoSaga e) {
        return processar().executar(new ProcessarEventoSagaUseCase.Input(UUID.randomUUID(), os, e));
    }

    private OrdemServico ateAguardandoAprovacao() {
        OrdemServico os = abrirOsDaAlice();
        evento(os.id(), new EventoSaga.OrdemExecucaoAberta());
        evento(os.id(), new EventoSaga.DiagnosticoConcluido(AGORA, AGORA.plusMinutes(30), itensDiagnostico()));
        evento(os.id(), new EventoSaga.OrcamentoGerado(UUID.randomUUID(), Dinheiro.de("310.00"), AGORA.plusMinutes(31)));
        return os;
    }

    // ---------- abertura ----------

    @Test
    @DisplayName("abrir grava OS, saga e o comando AbrirOrdemExecucao — os três juntos")
    void abrirGravaOsSagaEOutbox() {
        OrdemServico os = abrirOsDaAlice();

        assertEquals(StatusOS.RECEBIDA, ordens.dados.get(os.id()).status());
        assertEquals(EstadoSaga.EXECUCAO_SOLICITADA, sagas.dados.get(os.id()).estado());
        assertEquals(List.of("AbrirOrdemExecucao"), outbox.tipos());
        assertEquals(os.id(), outbox.enfileirados.get(0).correlationId());
        assertNull(outbox.enfileirados.get(0).causationId(), "causa é ação de usuário, não mensagem");
        assertEquals(1, ordens.historico.size());
    }

    @Test
    void abrirCadastraClienteEVeiculoNovos() {
        OrdemServico os = abrir().executar(new AbrirOrdemServicoUseCase.Input(
                new AbrirOrdemServicoUseCase.DadosCliente("111.444.777-35", "Bruno Lima", "b@x.com", null),
                new AbrirOrdemServicoUseCase.DadosVeiculo("XYZ9A87", "VW", "Golf", 2020),
                null));

        assertEquals(2, clientes.dados.size());
        assertEquals(2, veiculos.dados.size());
        assertTrue(veiculos.dados.containsKey(os.veiculoId()));
    }

    @Test
    void abrirSemDadosParaCadastrarFalha() {
        assertThrows(ClienteNaoEncontradoException.class, () -> abrir().executar(new AbrirOrdemServicoUseCase.Input(
                new AbrirOrdemServicoUseCase.DadosCliente("111.444.777-35", " ", null, null),
                new AbrirOrdemServicoUseCase.DadosVeiculo("ABC1D23", null, null, null), null)));
        assertThrows(VeiculoNaoEncontradoException.class, () -> abrir().executar(new AbrirOrdemServicoUseCase.Input(
                new AbrirOrdemServicoUseCase.DadosCliente("529.982.247-25", null, null, null),
                new AbrirOrdemServicoUseCase.DadosVeiculo("XYZ9A87", "VW", null, 2020), null)));
        assertTrue(outbox.enfileirados.isEmpty());
    }

    // ---------- eventos ----------

    @Test
    void eventoAplicadoGravaTudoComCausaDaMensagem() {
        OrdemServico os = abrirOsDaAlice();
        UUID mensagem = UUID.randomUUID();
        evento(os.id(), new EventoSaga.OrdemExecucaoAberta());

        EfeitoSaga efeito = processar().executar(new ProcessarEventoSagaUseCase.Input(mensagem, os.id(),
                new EventoSaga.DiagnosticoConcluido(AGORA, AGORA.plusMinutes(30), itensDiagnostico())));

        assertTrue(efeito.resultado().aplicado());
        assertTrue(efeito.statusMudou());
        assertTrue(efeito.entrouEm(EstadoSaga.ORCAMENTO_SOLICITADO));
        assertEquals(List.of("AbrirOrdemExecucao", "GerarOrcamento"), outbox.tipos());
        assertEquals(mensagem, outbox.enfileirados.get(1).causationId());
        assertEquals(2, ordens.dados.get(os.id()).itens().size());
    }

    @Test
    @DisplayName("mensagem repetida (mesmo messageId) não tem efeito nenhum")
    void inboxDescartaDuplicata() {
        OrdemServico os = abrirOsDaAlice();
        UUID mensagem = UUID.randomUUID();
        processar().executar(new ProcessarEventoSagaUseCase.Input(mensagem, os.id(), new EventoSaga.OrdemExecucaoAberta()));

        EfeitoSaga repetida = processar().executar(
                new ProcessarEventoSagaUseCase.Input(mensagem, os.id(), new EventoSaga.OrdemExecucaoAberta()));

        assertTrue(repetida.duplicada());
        assertFalse(repetida.statusMudou());
        assertFalse(repetida.entrouEm(EstadoSaga.EM_DIAGNOSTICO));
    }

    @Test
    void eventoIgnoradoNaoGravaNada() {
        OrdemServico os = abrirOsDaAlice();

        EfeitoSaga efeito = evento(os.id(), new EventoSaga.PagamentoConfirmado(AGORA));

        assertFalse(efeito.resultado().aplicado());
        assertEquals(List.of("AbrirOrdemExecucao"), outbox.tipos());
    }

    @Test
    void eventoDeOsDesconhecidaEIgnorado() {
        EfeitoSaga efeito = evento(OrdemServicoId.novo(), new EventoSaga.OrdemExecucaoAberta());

        assertFalse(efeito.resultado().aplicado());
        assertNull(efeito.os());
    }

    @Test
    void osSemClienteEDefeitoDeDados() {
        OrdemServico os = abrirOsDaAlice();
        clientes.dados.clear();

        assertThrows(IllegalStateException.class, () -> evento(os.id(), new EventoSaga.OrdemExecucaoAberta()));
    }

    // ---------- decisão ----------

    @Test
    void clienteAprovaAPropriaOs() {
        OrdemServico os = ateAguardandoAprovacao();

        EfeitoSaga efeito = decidir().executar(new DecidirOrcamentoUseCase.Input(os.id(), true, alice.id()));

        assertEquals(StatusOS.AGUARDANDO_PAGAMENTO, efeito.os().status());
        assertEquals("SolicitarPagamento", outbox.tipos().get(outbox.tipos().size() - 1));
    }

    @Test
    @DisplayName("o cliente curioso: Bruno não decide o orçamento da OS da Alice (403)")
    void clienteNaoDecideOsDeOutro() {
        OrdemServico os = ateAguardandoAprovacao();
        int comandosAntes = outbox.enfileirados.size();

        assertThrows(AcessoNegadoAOrdemServicoException.class,
                () -> decidir().executar(new DecidirOrcamentoUseCase.Input(os.id(), true, bruno().id())));
        assertEquals(comandosAntes, outbox.enfileirados.size());
        assertEquals(EstadoSaga.AGUARDANDO_APROVACAO, sagas.dados.get(os.id()).estado());
    }

    @Test
    void atendenteRecusaEmNomeDoCliente() {
        OrdemServico os = ateAguardandoAprovacao();

        EfeitoSaga efeito = decidir().executar(new DecidirOrcamentoUseCase.Input(os.id(), false, null));

        assertEquals(StatusOS.CANCELADA, efeito.os().status());
        assertTrue(efeito.entrouEm(EstadoSaga.COMPENSANDO));
        assertTrue(outbox.tipos().containsAll(List.of("CancelarOrdemExecucao", "CancelarOrcamento")));
    }

    @Test
    void decidirOsInexistenteOuForaDeHora() {
        assertThrows(OrdemServicoNaoEncontradaException.class,
                () -> decidir().executar(new DecidirOrcamentoUseCase.Input(OrdemServicoId.novo(), true, null)));
        OrdemServico os = abrirOsDaAlice();
        assertThrows(TransicaoSagaInvalidaException.class,
                () -> decidir().executar(new DecidirOrcamentoUseCase.Input(os.id(), true, null)));
    }

    // ---------- prazos e reprocessamento ----------

    @Test
    @DisplayName("verificador de prazos: reenvia, depois falha; reprocessar retoma o passo")
    void prazosEReprocessamento() {
        OrdemServico os = abrirOsDaAlice();
        VerificarPrazosSagaUseCase prazos = new VerificarPrazosSagaUseCase(sagas, ordens, outbox, PRAZOS, relogio);

        assertTrue(prazos.vencidas().isEmpty());
        relogio.avancar(Duration.ofSeconds(31));
        assertEquals(List.of(os.id()), prazos.vencidas());

        EfeitoSaga reenvio = prazos.verificar(os.id()).orElseThrow();
        assertEquals(List.of("AbrirOrdemExecucao"), reenvio.resultado().comandos().stream().map(c -> c.tipo()).toList());
        assertTrue(prazos.verificar(os.id()).isEmpty(), "prazo empurrado: segunda passada não faz nada");

        while (sagas.dados.get(os.id()).estado() != EstadoSaga.FALHA) {
            relogio.avancar(Duration.ofHours(1));
            prazos.verificar(os.id());
        }

        ReprocessarSagaUseCase reprocessar = new ReprocessarSagaUseCase(sagas, ordens, outbox, PRAZOS, relogio);
        EfeitoSaga retomada = reprocessar.executar(os.id());
        assertEquals(EstadoSaga.EXECUCAO_SOLICITADA, retomada.saga().estado());
        assertEquals("AbrirOrdemExecucao", outbox.tipos().get(outbox.tipos().size() - 1));
        assertTrue(prazos.verificar(OrdemServicoId.novo()).isEmpty(), "OS sem saga");
        assertThrows(OrdemServicoNaoEncontradaException.class, () -> reprocessar.executar(OrdemServicoId.novo()));
    }

    // ---------- entrega e histórico ----------

    @Test
    void entregaEHistorico() {
        OrdemServico os = ateAguardandoAprovacao();
        decidir().executar(new DecidirOrcamentoUseCase.Input(os.id(), true, null));
        evento(os.id(), new EventoSaga.PagamentoConfirmado(AGORA.plusHours(1)));
        evento(os.id(), new EventoSaga.ReparoFinalizado(AGORA.plusHours(3)));

        OrdemServico entregue = new EntregarVeiculoUseCase(ordens, relogio).executar(os.id());

        assertEquals(StatusOS.ENTREGUE, entregue.status());
        List<TransicaoStatus> historico = new ConsultarHistoricoOSUseCase(ordens).executar(os.id());
        assertEquals(StatusOS.ENTREGUE, historico.get(historico.size() - 1).para());
        assertSame(entregue, ordens.dados.get(os.id()));
        assertThrows(OrdemServicoNaoEncontradaException.class,
                () -> new ConsultarHistoricoOSUseCase(ordens).executar(OrdemServicoId.novo()));
        assertThrows(OrdemServicoNaoEncontradaException.class,
                () -> new EntregarVeiculoUseCase(ordens, relogio).executar(OrdemServicoId.novo()));
    }

    @Test
    void sagaNovaNaoTemFalha() {
        SagaOrdemServico saga = sagas.dados.isEmpty() ? null : sagas.dados.values().iterator().next();
        assertNull(saga);
        abrirOsDaAlice();
        assertNull(sagas.dados.values().iterator().next().motivoFalha());
    }
}
