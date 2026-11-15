package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento.AprovadoPor;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.AGORA;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.alice;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.argoDe;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.bruno;
import static br.com.fiap.techchallenge.oficina.os.suporte.Fixtures.itensDiagnostico;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdemServicoTest {

    private Cliente cliente;
    private OrdemServico os;

    @BeforeEach
    void abrir() {
        cliente = alice();
        os = OrdemServico.abrir(cliente.id(), argoDe(cliente).id(), "  Barulho na suspensão  ", AGORA);
    }

    private ResumoOrcamento orcamento() {
        return ResumoOrcamento.gerado(UUID.randomUUID(), Dinheiro.de("310.00"), AGORA.plusHours(1));
    }

    private void ateAguardandoPagamento() {
        os.iniciarDiagnostico(AGORA.plusMinutes(10));
        os.concluirDiagnostico(itensDiagnostico(), AGORA.plusMinutes(10), AGORA.plusMinutes(50));
        os.registrarOrcamento(orcamento());
        os.aprovarOrcamento(AprovadoPor.CLIENTE, AGORA.plusHours(2));
    }

    @Test
    void abreRecebidaComRelatoAparadoEHistorico() {
        assertEquals(StatusOS.RECEBIDA, os.status());
        assertEquals("Barulho na suspensão", os.relatoCliente());
        assertEquals(AGORA, os.criadaEm());
        assertEquals(AGORA, os.atualizadaEm());
        assertEquals(1, os.novasTransicoes().size());
        assertNull(os.novasTransicoes().get(0).de());
        assertEquals(StatusOS.RECEBIDA, os.novasTransicoes().get(0).para());
        assertTrue(os.itens().isEmpty());
        assertFalse(os.pagamento().confirmado());
    }

    @Test
    void relatoEmBrancoViraNulo() {
        OrdemServico semRelato = OrdemServico.abrir(cliente.id(), argoDe(cliente).id(), "   ", AGORA);
        assertNull(semRelato.relatoCliente());
    }

    @Test
    void cicloCompletoRegistraCadaTransicaoNoHistorico() {
        ateAguardandoPagamento();
        os.registrarLinkPagamento("https://mp.test/1", AGORA.plusHours(3));
        os.confirmarPagamento(AGORA.plusHours(2).plusMinutes(10));
        os.finalizar(AGORA.plusHours(6));
        os.entregar(AGORA.plusHours(8));

        assertEquals(StatusOS.ENTREGUE, os.status());
        assertEquals(List.of(StatusOS.RECEBIDA, StatusOS.EM_DIAGNOSTICO, StatusOS.AGUARDANDO_APROVACAO,
                        StatusOS.AGUARDANDO_PAGAMENTO, StatusOS.EM_EXECUCAO, StatusOS.FINALIZADA, StatusOS.ENTREGUE),
                os.novasTransicoes().stream().map(TransicaoStatus::para).toList());
        assertTrue(os.orcamento().aprovado());
        assertEquals(AprovadoPor.CLIENTE, os.orcamento().aprovadoPor());
        assertTrue(os.pagamento().confirmado());
        assertEquals("https://mp.test/1", os.pagamento().link());
        assertEquals(AGORA.plusHours(2).plusMinutes(10), os.execucaoIniciadaEm());
        assertEquals(AGORA.plusHours(8), os.atualizadaEm());
        assertEquals(Dinheiro.de("310.00"), os.totalDosItens());

        os.transicoesPersistidas();
        assertTrue(os.novasTransicoes().isEmpty());
    }

    @Test
    void concluirDiagnosticoDiretoDeRecebidaUsaOInicioInformado() {
        os.concluirDiagnostico(itensDiagnostico(), AGORA.plusMinutes(7), AGORA.plusMinutes(30));

        assertEquals(StatusOS.EM_DIAGNOSTICO, os.status());
        assertEquals(AGORA.plusMinutes(7), os.diagnosticoIniciadoEm());
        assertEquals(2, os.itens().size());
    }

    @Test
    void concluirDiagnosticoSemInicioUsaOMomentoDaConclusao() {
        os.concluirDiagnostico(List.of(), null, AGORA.plusMinutes(30));
        assertEquals(AGORA.plusMinutes(30), os.diagnosticoIniciadoEm());
    }

    @Test
    void transicoesForaDeOrdemSaoRecusadas() {
        assertThrows(TransicaoStatusInvalidaException.class, () -> os.registrarOrcamento(orcamento()));
        assertThrows(TransicaoStatusInvalidaException.class, () -> os.aprovarOrcamento(AprovadoPor.CLIENTE, AGORA));
        assertThrows(TransicaoStatusInvalidaException.class, () -> os.confirmarPagamento(AGORA));
        assertThrows(TransicaoStatusInvalidaException.class, () -> os.finalizar(AGORA));
        assertThrows(TransicaoStatusInvalidaException.class, () -> os.entregar(AGORA));
        assertThrows(TransicaoStatusInvalidaException.class, () -> os.registrarLinkPagamento("x", AGORA));

        os.iniciarDiagnostico(AGORA);
        assertThrows(TransicaoStatusInvalidaException.class, () -> os.iniciarDiagnostico(AGORA));
        os.registrarOrcamento(orcamento());
        assertThrows(TransicaoStatusInvalidaException.class,
                () -> os.concluirDiagnostico(itensDiagnostico(), AGORA, AGORA));
    }

    @Test
    void cancelarRegistraMotivoEImpedeNovoCancelamento() {
        ateAguardandoPagamento();

        os.cancelar(MotivoCancelamento.PAGAMENTO_RECUSADO, AGORA.plusHours(3));

        assertEquals(StatusOS.CANCELADA, os.status());
        assertEquals(MotivoCancelamento.PAGAMENTO_RECUSADO, os.motivoCancelamento());
        assertEquals(AGORA.plusHours(3), os.canceladaEm());
        assertEquals("Pagamento recusado", os.novasTransicoes().get(os.novasTransicoes().size() - 1).motivo());
        assertThrows(TransicaoStatusInvalidaException.class,
                () -> os.cancelar(MotivoCancelamento.PAGAMENTO_EXPIRADO, AGORA));
    }

    @Test
    void osFinalizadaNaoPodeSerCancelada() {
        ateAguardandoPagamento();
        os.confirmarPagamento(AGORA.plusHours(3));
        os.finalizar(AGORA.plusHours(5));

        assertFalse(os.status().cancelavel());
        assertThrows(TransicaoStatusInvalidaException.class,
                () -> os.cancelar(MotivoCancelamento.REPARO_NAO_LIBERADO, AGORA));
    }

    @Test
    void pertenceSoAoProprioCliente() {
        assertTrue(os.pertenceA(cliente.id()));
        assertFalse(os.pertenceA(bruno().id()));
    }

    @Test
    void reconstituirNaoGeraHistorico() {
        OrdemServico.Dados d = new OrdemServico.Dados();
        d.id = os.id();
        d.clienteId = os.clienteId();
        d.veiculoId = os.veiculoId();
        d.status = StatusOS.AGUARDANDO_PAGAMENTO;
        d.criadaEm = AGORA;
        d.orcamento = orcamento();

        OrdemServico reconstituida = OrdemServico.reconstituir(d);

        assertTrue(reconstituida.novasTransicoes().isEmpty());
        assertEquals(Pagamento.NENHUM, reconstituida.pagamento());
        assertEquals(os, reconstituida, "identidade é o id");
        assertEquals(os.hashCode(), reconstituida.hashCode());
        assertNotEquals(os, OrdemServico.abrir(cliente.id(), os.veiculoId(), null, AGORA));
    }

    @Test
    void valueObjectsValidamSeusDados() {
        UUID ref = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new ItemOS(ItemOS.TipoItem.PECA, ref, " ", 1, Dinheiro.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemOS(ItemOS.TipoItem.PECA, ref, "Filtro", 0, Dinheiro.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ResumoOrcamento(ref, Dinheiro.ZERO, AGORA, AGORA, null));
        assertEquals("Prazo para pagamento venceu", MotivoCancelamento.PAGAMENTO_EXPIRADO.descricao());
        assertEquals("Aguardando Pagamento", StatusOS.AGUARDANDO_PAGAMENTO.descricao());
        assertTrue(StatusOS.EM_EXECUCAO.prioridadeListagem() < StatusOS.AGUARDANDO_PAGAMENTO.prioridadeListagem());
        assertFalse(StatusOS.CANCELADA.visivelNaListagemPadrao());
    }
}
