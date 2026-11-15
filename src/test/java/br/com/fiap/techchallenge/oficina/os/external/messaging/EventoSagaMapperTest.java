package br.com.fiap.techchallenge.oficina.os.external.messaging;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ItemOS;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventoSagaMapperTest {

    private static Envelope envelope(String tipo, JsonObject payload) {
        return Envelope.de(EnvelopeTest.envelopeValido().put("type", tipo).put("payload", payload));
    }

    private static EventoSaga traduzir(String tipo, JsonObject payload) {
        return EventoSagaMapper.traduzir(envelope(tipo, payload)).orElseThrow();
    }

    @Test
    void diagnosticoConcluidoTrazItensEInicio() {
        UUID peca = UUID.randomUUID();
        JsonObject payload = new JsonObject()
                .put("diagnosticoIniciadoEm", "2026-11-15T10:00:00Z")
                .put("concluidoEm", "2026-11-15T10:30:00Z")
                .put("itens", new JsonArray().add(new JsonObject()
                        .put("tipo", "PECA").put("referenciaId", peca.toString()).put("descricao", "Pastilha")
                        .put("quantidade", 2).put("valorUnitario", "95.00")));

        EventoSaga.DiagnosticoConcluido e = (EventoSaga.DiagnosticoConcluido) traduzir("DiagnosticoConcluido", payload);

        assertEquals(OffsetDateTime.parse("2026-11-15T10:00:00Z"), e.iniciadoEm());
        assertEquals(1, e.itens().size());
        ItemOS item = e.itens().get(0);
        assertEquals(peca, item.referenciaId());
        assertEquals(Dinheiro.de("190.00"), item.subtotal());
    }

    @Test
    void orcamentoGeradoEPagamentoSolicitado() {
        UUID orc = UUID.randomUUID();
        EventoSaga.OrcamentoGerado gerado = (EventoSaga.OrcamentoGerado) traduzir("OrcamentoGerado", new JsonObject()
                .put("orcamentoId", orc.toString()).put("valorTotal", "310.00").put("geradoEm", "2026-11-15T11:00:00Z"));
        assertEquals(orc, gerado.orcamentoId());
        assertEquals(Dinheiro.de("310.00"), gerado.valorTotal());

        EventoSaga.PagamentoSolicitado solicitado = (EventoSaga.PagamentoSolicitado) traduzir("PagamentoSolicitado",
                new JsonObject().put("linkPagamento", "https://mp.test/1"));
        assertEquals("https://mp.test/1", solicitado.linkPagamento());
        assertNull(solicitado.expiraEm());
    }

    @Test
    void instanteAusenteUsaOOccurredAtDoEnvelope() {
        EventoSaga.PagamentoConfirmado e = (EventoSaga.PagamentoConfirmado) traduzir("PagamentoConfirmado", new JsonObject());
        assertEquals(OffsetDateTime.parse("2026-11-15T14:03:21.512Z"), e.confirmadoEm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OrdemExecucaoAberta", "DiagnosticoIniciado", "ReparoLiberado", "ReparoNaoLiberado",
            "ReparoIniciado", "ReparoFinalizado", "OrdemExecucaoCancelada", "OrcamentoNaoGerado", "PagamentoConfirmado",
            "PagamentoRecusado", "PagamentoExpirado", "OrcamentoCancelado", "PagamentoEstornado", "EstornoFalhou"})
    void todoEventoDoContratoETraduzido(String tipo) {
        EventoSaga evento = traduzir(tipo, new JsonObject().put("motivo", "x").put("erro", "y"));
        assertEquals(tipo, evento.tipo());
    }

    @Test
    void motivoETextoLivre() {
        EventoSaga.ReparoNaoLiberado e = (EventoSaga.ReparoNaoLiberado) traduzir("ReparoNaoLiberado",
                new JsonObject().put("motivo", "ESTOQUE_INSUFICIENTE"));
        assertEquals("ESTOQUE_INSUFICIENTE", e.motivo());
        assertNull(((EventoSaga.PagamentoRecusado) traduzir("PagamentoRecusado", new JsonObject())).motivo());
    }

    @Test
    void tipoDesconhecidoNaoEErro() {
        assertTrue(EventoSagaMapper.traduzir(envelope("EventoDoFuturo", new JsonObject())).isEmpty());
    }

    @Test
    void camposObrigatoriosInvalidosVaoParaADlq() {
        assertThrows(EnvelopeInvalidoException.class, () -> traduzir("OrcamentoGerado", new JsonObject()));
        assertThrows(EnvelopeInvalidoException.class, () -> traduzir("OrcamentoGerado",
                new JsonObject().put("orcamentoId", "nao-uuid").put("valorTotal", "1.00")));
        assertThrows(EnvelopeInvalidoException.class, () -> traduzir("DiagnosticoConcluido",
                new JsonObject().put("itens", "nao-e-lista")));
        assertThrows(EnvelopeInvalidoException.class, () -> traduzir("DiagnosticoConcluido",
                new JsonObject().put("itens", new JsonArray().add(new JsonObject().put("tipo", "MOTOR")))));
        assertThrows(EnvelopeInvalidoException.class, () -> traduzir("PagamentoConfirmado",
                new JsonObject().put("confirmadoEm", "ontem")));
        assertInstanceOf(EventoSaga.PagamentoExpirado.class, traduzir("PagamentoExpirado", new JsonObject()));
    }
}
