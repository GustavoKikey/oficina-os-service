package br.com.fiap.techchallenge.oficina.os.saga.entities;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.MotivoCancelamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento.AprovadoPor;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Veiculo;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.DiagnosticoConcluido;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.DiagnosticoIniciado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.EstornoFalhou;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrcamentoCancelado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrcamentoGerado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrcamentoNaoGerado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrdemExecucaoAberta;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrdemExecucaoCancelada;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoConfirmado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoEstornado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoExpirado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoRecusado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoSolicitado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.ReparoFinalizado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.ReparoIniciado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.ReparoLiberado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.ReparoNaoLiberado;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.AGORA;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.PRAZOS;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.alice;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.argoDe;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.bruno;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.itensDiagnostico;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.osAberta;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaOrdemServicoTest {

    private static final UUID ORCAMENTO = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private Cliente cliente;
    private Veiculo veiculo;
    private OrdemServico os;
    private SagaOrdemServico saga;
    private List<ComandoSaga> primeirosComandos;

    @BeforeEach
    void abrirOs() {
        cliente = alice();
        veiculo = argoDe(cliente);
        os = osAberta(cliente, veiculo);
        SagaOrdemServico.Inicio inicio = SagaOrdemServico.iniciar(os, veiculo, PRAZOS, AGORA);
        saga = inicio.saga();
        primeirosComandos = inicio.comandos();
    }

    // ---------- helpers ----------

    private ResultadoSaga receber(EventoSaga evento) {
        return saga.aoReceber(evento, os, cliente, PRAZOS, AGORA);
    }

    private ResultadoSaga receber(EventoSaga evento, OffsetDateTime quando) {
        return saga.aoReceber(evento, os, cliente, PRAZOS, quando);
    }

    private void ateAguardandoAprovacao() {
        receber(new OrdemExecucaoAberta());
        receber(new DiagnosticoIniciado(AGORA.plusMinutes(10)));
        receber(new DiagnosticoConcluido(AGORA.plusMinutes(10), AGORA.plusMinutes(40), itensDiagnostico()));
        receber(new OrcamentoGerado(ORCAMENTO, Dinheiro.de("310.00"), AGORA.plusMinutes(41)));
    }

    private void ateAguardandoPagamento() {
        ateAguardandoAprovacao();
        saga.aprovarOrcamento(os, AprovadoPor.CLIENTE, PRAZOS, AGORA.plusHours(1));
    }

    private void ateReparoSolicitado() {
        ateAguardandoPagamento();
        receber(new PagamentoSolicitado("https://mp.test/checkout/1", AGORA.plusHours(1).plusMinutes(30)));
        receber(new PagamentoConfirmado(AGORA.plusHours(1).plusMinutes(5)));
    }

    private static List<String> tipos(List<ComandoSaga> comandos) {
        return comandos.stream().map(ComandoSaga::tipo).toList();
    }

    // ---------- início ----------

    @Test
    @DisplayName("abrir a OS inicia a saga pedindo à Execução que coloque a OS na fila, com os dados do veículo")
    void inicioPedeAberturaDaOrdemDeExecucao() {
        assertEquals(EstadoSaga.EXECUCAO_SOLICITADA, saga.estado());
        assertEquals(AGORA.plusSeconds(30), saga.prazo());
        assertEquals(1, primeirosComandos.size());

        ComandoSaga abrir = primeirosComandos.get(0);
        assertEquals("AbrirOrdemExecucao", abrir.tipo());
        assertEquals(ComandoSaga.Destino.EXECUCAO, abrir.destino());
        assertEquals(os.id().valor().toString(), abrir.payload().get("osId"));
        assertEquals("Barulho na suspensão", abrir.payload().get("relatoCliente"));
        @SuppressWarnings("unchecked")
        Map<String, Object> v = (Map<String, Object>) abrir.payload().get("veiculo");
        assertEquals("ABC1D23", v.get("placa"));
        assertEquals(2022, v.get("ano"));
        assertEquals(primeirosComandos, saga.comandosPendentes());
    }

    @Test
    void naoIniciaComVeiculoDeOutraOs() {
        Veiculo outro = argoDe(bruno());
        assertThrows(IllegalArgumentException.class, () -> SagaOrdemServico.iniciar(os, outro, PRAZOS, AGORA));
    }

    // ---------- fluxo feliz ----------

    @Test
    @DisplayName("fluxo feliz: diagnóstico → orçamento → aprovação → pagamento → reparo → concluída")
    void fluxoFelizCompleto() {
        assertTrue(receber(new OrdemExecucaoAberta()).aplicado());
        assertEquals(EstadoSaga.EM_DIAGNOSTICO, saga.estado());
        assertNull(saga.prazo(), "aguardando o mecânico: sem prazo técnico");

        receber(new DiagnosticoIniciado(AGORA.plusMinutes(10)));
        assertEquals(StatusOS.EM_DIAGNOSTICO, os.status());

        ResultadoSaga diag = receber(new DiagnosticoConcluido(AGORA.plusMinutes(10), AGORA.plusMinutes(40),
                itensDiagnostico()));
        assertEquals(EstadoSaga.ORCAMENTO_SOLICITADO, saga.estado());
        assertEquals(List.of("GerarOrcamento"), tipos(diag.comandos()));
        ComandoSaga gerar = diag.comandos().get(0);
        assertEquals(ComandoSaga.Destino.BILLING, gerar.destino());
        @SuppressWarnings("unchecked")
        Map<String, Object> dadosCliente = (Map<String, Object>) gerar.payload().get("cliente");
        assertEquals("alice@exemplo.com", dadosCliente.get("email"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) gerar.payload().get("itens");
        assertEquals(2, itens.size());
        assertEquals("95.00", itens.get(1).get("valorUnitario"));
        assertEquals(2, os.itens().size());

        receber(new OrcamentoGerado(ORCAMENTO, Dinheiro.de("310.00"), AGORA.plusMinutes(41)));
        assertEquals(EstadoSaga.AGUARDANDO_APROVACAO, saga.estado());
        assertEquals(StatusOS.AGUARDANDO_APROVACAO, os.status());
        assertEquals(AGORA.plusHours(72), saga.prazo(), "prazo de aprovação conta a partir do evento");

        ResultadoSaga aprovacao = saga.aprovarOrcamento(os, AprovadoPor.CLIENTE, PRAZOS, AGORA.plusHours(1));
        assertEquals(List.of("SolicitarPagamento"), tipos(aprovacao.comandos()));
        assertEquals("CLIENTE", aprovacao.comandos().get(0).payload().get("aprovadoPor"));
        assertEquals(EstadoSaga.PAGAMENTO_SOLICITADO, saga.estado());
        assertEquals(StatusOS.AGUARDANDO_PAGAMENTO, os.status());
        assertEquals(AGORA.plusHours(1).plusMinutes(35), saga.prazo());

        receber(new PagamentoSolicitado("https://mp.test/checkout/1", AGORA.plusHours(1).plusMinutes(30)));
        assertEquals("https://mp.test/checkout/1", os.pagamento().link());
        assertTrue(saga.comandosPendentes().isEmpty(), "Billing respondeu: agora espera-se o cliente pagar");

        ResultadoSaga pago = receber(new PagamentoConfirmado(AGORA.plusHours(1).plusMinutes(5)));
        assertEquals(List.of("LiberarParaReparo"), tipos(pago.comandos()));
        assertEquals(ORCAMENTO.toString(), pago.comandos().get(0).payload().get("orcamentoId"));
        assertEquals(EstadoSaga.REPARO_SOLICITADO, saga.estado());
        assertEquals(StatusOS.EM_EXECUCAO, os.status());

        receber(new ReparoLiberado(AGORA.plusHours(2)));
        assertEquals(EstadoSaga.EM_REPARO, saga.estado());

        receber(new ReparoFinalizado(AGORA.plusHours(5)));
        assertEquals(EstadoSaga.CONCLUIDA, saga.estado());
        assertEquals(StatusOS.FINALIZADA, os.status());
        assertTrue(saga.estado().encerrada());
    }

    // ---------- ordem e duplicidade ----------

    @Test
    @DisplayName("DiagnosticoConcluido chegando antes dos eventos anteriores é aceito e usa o início que traz")
    void diagnosticoConcluidoForaDeOrdem() {
        ResultadoSaga r = receber(new DiagnosticoConcluido(AGORA.plusMinutes(5), AGORA.plusMinutes(20),
                itensDiagnostico()));

        assertTrue(r.aplicado());
        assertEquals(EstadoSaga.ORCAMENTO_SOLICITADO, saga.estado());
        assertEquals(StatusOS.EM_DIAGNOSTICO, os.status());
        assertEquals(AGORA.plusMinutes(5), os.diagnosticoIniciadoEm());

        assertFalse(receber(new DiagnosticoIniciado(AGORA.plusMinutes(5))).aplicado(), "início atrasado é ignorado");
        assertFalse(receber(new OrdemExecucaoAberta()).aplicado());
    }

    @Test
    void diagnosticoIniciadoSupreAberturaPerdida() {
        receber(new DiagnosticoIniciado(AGORA.plusMinutes(3)));

        assertEquals(EstadoSaga.EM_DIAGNOSTICO, saga.estado());
        assertEquals(StatusOS.EM_DIAGNOSTICO, os.status());
        assertTrue(saga.comandosPendentes().isEmpty());
    }

    @Test
    void eventoRepetidoNaoTemEfeito() {
        ateAguardandoAprovacao();

        ResultadoSaga repetido = receber(new OrcamentoGerado(ORCAMENTO, Dinheiro.de("310.00"), AGORA));

        assertFalse(repetido.aplicado());
        assertTrue(repetido.comandos().isEmpty());
        assertEquals(EstadoSaga.AGUARDANDO_APROVACAO, saga.estado());
    }

    @Test
    void eventosQueNaoCabemNoEstadoSaoIgnorados() {
        assertFalse(receber(new PagamentoConfirmado(AGORA)).aplicado());
        assertFalse(receber(new ReparoFinalizado(AGORA)).aplicado());
        assertFalse(receber(new OrdemExecucaoCancelada()).aplicado(), "nada sendo compensado");
        assertFalse(receber(new EstornoFalhou("x")).aplicado());
        assertFalse(receber(new ReparoIniciado()).aplicado());
        assertFalse(receber(new PagamentoSolicitado("l", AGORA)).aplicado());
        assertEquals(EstadoSaga.EXECUCAO_SOLICITADA, saga.estado());
    }

    // ---------- compensações ----------

    @Test
    @DisplayName("orçamento não gerado: libera as peças e cancela a OS")
    void orcamentoNaoGeradoCompensaExecucao() {
        receber(new OrdemExecucaoAberta());
        receber(new DiagnosticoConcluido(AGORA, AGORA.plusMinutes(10), itensDiagnostico()));

        ResultadoSaga r = receber(new OrcamentoNaoGerado("itens inválidos"));

        assertEquals(List.of("CancelarOrdemExecucao"), tipos(r.comandos()));
        assertEquals("ORCAMENTO_NAO_GERADO", r.comandos().get(0).payload().get("motivo"));
        assertEquals(EstadoSaga.COMPENSANDO, saga.estado());
        assertEquals(StatusOS.CANCELADA, os.status());
        assertEquals(MotivoCancelamento.ORCAMENTO_NAO_GERADO, os.motivoCancelamento());

        receber(new OrdemExecucaoCancelada());
        assertEquals(EstadoSaga.COMPENSADA, saga.estado());
        assertNull(saga.prazo());
    }

    @Test
    @DisplayName("cliente recusa: compensa Execução e Billing; só fecha quando as duas confirmam")
    void recusaCompensaOsDoisParticipantes() {
        ateAguardandoAprovacao();

        ResultadoSaga r = saga.recusarOrcamento(os, PRAZOS, AGORA.plusHours(2));

        assertEquals(List.of("CancelarOrdemExecucao", "CancelarOrcamento"), tipos(r.comandos()));
        assertEquals(ORCAMENTO.toString(), r.comandos().get(1).payload().get("orcamentoId"));
        assertEquals(StatusOS.CANCELADA, os.status());
        assertEquals(MotivoCancelamento.ORCAMENTO_RECUSADO, os.motivoCancelamento());

        assertTrue(receber(new OrcamentoCancelado()).aplicado());
        assertEquals(EstadoSaga.COMPENSANDO, saga.estado(), "falta a confirmação da Execução");
        assertFalse(receber(new OrcamentoCancelado()).aplicado(), "confirmação repetida");

        receber(new OrdemExecucaoCancelada());
        assertEquals(EstadoSaga.COMPENSADA, saga.estado());
    }

    @Test
    void aprovarDuasVezesEhConflito() {
        ateAguardandoPagamento();

        assertThrows(TransicaoSagaInvalidaException.class,
                () -> saga.aprovarOrcamento(os, AprovadoPor.CLIENTE, PRAZOS, AGORA));
        assertThrows(TransicaoSagaInvalidaException.class, () -> saga.recusarOrcamento(os, PRAZOS, AGORA));
    }

    @Test
    void atendenteAprovandoFicaRegistradoNaOs() {
        ateAguardandoAprovacao();

        ResultadoSaga r = saga.aprovarOrcamento(os, AprovadoPor.ATENDENTE, PRAZOS, AGORA);

        assertEquals("ATENDENTE", r.comandos().get(0).payload().get("aprovadoPor"));
        assertEquals(AprovadoPor.ATENDENTE, os.orcamento().aprovadoPor());
    }

    @Test
    @DisplayName("pagamento recusado ou expirado: nada a estornar — cancela orçamento e libera peças")
    void pagamentoRecusadoOuExpiradoCompensaSemEstorno() {
        ateAguardandoPagamento();
        ResultadoSaga recusado = receber(new PagamentoRecusado("cc_rejected_other_reason"));
        assertEquals(List.of("CancelarOrdemExecucao", "CancelarOrcamento"), tipos(recusado.comandos()));
        assertEquals(MotivoCancelamento.PAGAMENTO_RECUSADO, os.motivoCancelamento());

        abrirOs();
        ateAguardandoPagamento();
        ResultadoSaga expirado = receber(new PagamentoExpirado());
        assertEquals(List.of("CancelarOrdemExecucao", "CancelarOrcamento"), tipos(expirado.comandos()));
        assertEquals(MotivoCancelamento.PAGAMENTO_EXPIRADO, os.motivoCancelamento());
    }

    @Test
    @DisplayName("reparo não liberado depois do pagamento: estorna no Mercado Pago e cancela a ordem de execução")
    void reparoNaoLiberadoEstornaPagamento() {
        ateReparoSolicitado();

        ResultadoSaga r = receber(new ReparoNaoLiberado("ESTOQUE_INSUFICIENTE"));

        assertEquals(List.of("EstornarPagamento", "CancelarOrdemExecucao"), tipos(r.comandos()));
        assertEquals(StatusOS.CANCELADA, os.status());
        assertEquals(MotivoCancelamento.REPARO_NAO_LIBERADO, os.motivoCancelamento());

        receber(new PagamentoEstornado());
        receber(new OrdemExecucaoCancelada());
        assertEquals(EstadoSaga.COMPENSADA, saga.estado());
    }

    @Test
    @DisplayName("estorno que falhou leva a FALHA; confirmações que chegam depois fecham a compensação")
    void estornoFalhouEDepoisSeResolve() {
        ateReparoSolicitado();
        receber(new ReparoNaoLiberado("ESTOQUE_INSUFICIENTE"));

        ResultadoSaga falha = receber(new EstornoFalhou("timeout no Mercado Pago"));

        assertTrue(falha.falhou());
        assertEquals(EstadoSaga.FALHA, saga.estado());
        assertEquals(EstadoSaga.COMPENSANDO, saga.estadoAntesDaFalha());
        assertTrue(saga.motivoFalha().contains("timeout"));

        receber(new OrdemExecucaoCancelada());
        assertEquals(EstadoSaga.FALHA, saga.estado(), "ainda falta o estorno");
        receber(new PagamentoEstornado());
        assertEquals(EstadoSaga.COMPENSADA, saga.estado());
        assertNull(saga.motivoFalha());
    }

    // ---------- reparo ----------

    @Test
    void reparoIniciadoProvaLiberacaoPerdida() {
        ateReparoSolicitado();

        assertTrue(receber(new ReparoIniciado()).aplicado());
        assertEquals(EstadoSaga.EM_REPARO, saga.estado());
        assertFalse(receber(new ReparoIniciado()).aplicado(), "em EM_REPARO é só informativo");
    }

    @Test
    void reparoFinalizadoAntesDaLiberacaoConcluiASaga() {
        ateReparoSolicitado();

        receber(new ReparoFinalizado(AGORA.plusHours(4)));

        assertEquals(EstadoSaga.CONCLUIDA, saga.estado());
        assertEquals(AGORA.plusHours(4), os.finalizadaEm());
    }

    // ---------- prazos ----------

    @Test
    void prazoNaoVencidoNaoFazNada() {
        assertFalse(saga.verificarPrazo(os, PRAZOS, AGORA.plusSeconds(10)).aplicado());
    }

    @Test
    @DisplayName("participante calado: reenvia com backoff (60s, 120s, 240s) e, esgotado, FALHA")
    void reenviaComBackoffEDepoisFalha() {
        OffsetDateTime t = AGORA.plusSeconds(31);

        ResultadoSaga r1 = saga.verificarPrazo(os, PRAZOS, t);
        assertEquals(List.of("AbrirOrdemExecucao"), tipos(r1.comandos()));
        assertEquals(1, saga.tentativas());
        assertEquals(t.plusSeconds(60), saga.prazo());

        t = saga.prazo();
        saga.verificarPrazo(os, PRAZOS, t);
        assertEquals(t.plusSeconds(120), saga.prazo());
        t = saga.prazo();
        saga.verificarPrazo(os, PRAZOS, t);
        assertEquals(t.plusSeconds(240), saga.prazo());

        ResultadoSaga falha = saga.verificarPrazo(os, PRAZOS, saga.prazo());
        assertTrue(falha.falhou());
        assertEquals(EstadoSaga.FALHA, saga.estado());
        assertTrue(saga.motivoFalha().contains("AbrirOrdemExecucao"));
        assertEquals(StatusOS.RECEBIDA, os.status(), "silêncio não é recusa: a OS não é cancelada no escuro");
    }

    @Test
    @DisplayName("reprocessar volta a saga ao passo em que parou e reenvia o comando pendente")
    void reprocessarSagaEmFalha() {
        // Cada reenvio empurra o prazo: o relógio precisa acompanhar até esgotar.
        while (saga.estado() != EstadoSaga.FALHA) {
            saga.verificarPrazo(os, PRAZOS, saga.prazo());
        }
        assertEquals(EstadoSaga.EXECUCAO_SOLICITADA, saga.estadoAntesDaFalha());

        ResultadoSaga r = saga.reprocessar(PRAZOS, AGORA.plusDays(2));

        assertEquals(EstadoSaga.EXECUCAO_SOLICITADA, saga.estado());
        assertEquals(List.of("AbrirOrdemExecucao"), tipos(r.comandos()));
        assertEquals(0, saga.tentativas());
        assertEquals(AGORA.plusDays(2).plusSeconds(30), saga.prazo());
        assertNull(saga.motivoFalha());
    }

    @Test
    void soReprocessaSagaEmFalha() {
        assertThrows(TransicaoSagaInvalidaException.class, () -> saga.reprocessar(PRAZOS, AGORA));
    }

    @Test
    void prazoDeAprovacaoVencidoEquivaleARecusa() {
        ateAguardandoAprovacao();

        ResultadoSaga r = saga.verificarPrazo(os, PRAZOS, AGORA.plusHours(73));

        assertEquals(List.of("CancelarOrdemExecucao", "CancelarOrcamento"), tipos(r.comandos()));
        assertEquals(MotivoCancelamento.PRAZO_APROVACAO_VENCIDO, os.motivoCancelamento());
        assertEquals(EstadoSaga.COMPENSANDO, saga.estado());
    }

    @Test
    void prazoDePagamentoVencidoEquivaleAExpiracao() {
        ateAguardandoPagamento();

        saga.verificarPrazo(os, PRAZOS, AGORA.plusHours(2));

        assertEquals(MotivoCancelamento.PAGAMENTO_EXPIRADO, os.motivoCancelamento());
        assertEquals(EstadoSaga.COMPENSANDO, saga.estado());
    }

    @Test
    void compensacaoSemConfirmacaoTambemEReenviada() {
        ateAguardandoAprovacao();
        saga.recusarOrcamento(os, PRAZOS, AGORA);

        ResultadoSaga r = saga.verificarPrazo(os, PRAZOS, AGORA.plusMinutes(1));

        assertEquals(List.of("CancelarOrdemExecucao", "CancelarOrcamento"), tipos(r.comandos()));
        assertEquals(1, saga.tentativas());
    }

    // ---------- proteções ----------

    @Test
    void naoAceitaOsDeOutraSaga() {
        Cliente b = bruno();
        OrdemServico outra = osAberta(b, argoDe(b));

        assertThrows(IllegalArgumentException.class,
                () -> saga.aoReceber(new OrdemExecucaoAberta(), outra, b, PRAZOS, AGORA));
        assertThrows(IllegalArgumentException.class, () -> saga.verificarPrazo(outra, PRAZOS, AGORA));
    }

    @Test
    void diagnosticoExigeODonoDaOs() {
        assertThrows(IllegalArgumentException.class, () -> saga.aoReceber(
                new DiagnosticoConcluido(AGORA, AGORA, itensDiagnostico()), os, bruno(), PRAZOS, AGORA));
    }

    @Test
    void reconstituirPreservaOEstado() {
        ateAguardandoAprovacao();

        SagaOrdemServico copia = SagaOrdemServico.reconstituir(saga.osId(), saga.estado(), saga.estadoAntesDaFalha(),
                saga.prazo(), saga.tentativas(), saga.comandosPendentes(), saga.motivoFalha(), saga.iniciadaEm(),
                saga.atualizadaEm(), 7);

        assertEquals(EstadoSaga.AGUARDANDO_APROVACAO, copia.estado());
        assertEquals(saga.prazo(), copia.prazo());
        assertEquals(7, copia.versao());
        assertEquals(AGORA, copia.iniciadaEm());
    }

    @Test
    void estadosQueAguardamServico() {
        assertTrue(EstadoSaga.EXECUCAO_SOLICITADA.aguardaRespostaDeServico());
        assertTrue(EstadoSaga.COMPENSANDO.aguardaRespostaDeServico());
        assertFalse(EstadoSaga.AGUARDANDO_APROVACAO.aguardaRespostaDeServico());
        assertFalse(EstadoSaga.FALHA.encerrada());
        assertTrue(EstadoSaga.COMPENSADA.encerrada());
    }
}
