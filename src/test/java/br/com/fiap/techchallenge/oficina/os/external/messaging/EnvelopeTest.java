package br.com.fiap.techchallenge.oficina.os.external.messaging;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvelopeTest {

    static JsonObject envelopeValido() {
        return new JsonObject()
                .put("messageId", UUID.randomUUID().toString())
                .put("type", "OrcamentoGerado")
                .put("version", 1)
                .put("correlationId", UUID.randomUUID().toString())
                .put("causationId", UUID.randomUUID().toString())
                .put("occurredAt", "2026-11-15T14:03:21.512Z")
                .put("source", "billing-service")
                .put("payload", new JsonObject().put("valorTotal", "350.00"));
    }

    @Test
    void converteJsonObjectEmEnvelope() {
        JsonObject json = envelopeValido();

        Envelope envelope = Envelope.de(json);

        assertEquals(UUID.fromString(json.getString("messageId")), envelope.messageId());
        assertEquals("OrcamentoGerado", envelope.type());
        assertEquals(1, envelope.version());
        assertEquals(Instant.parse("2026-11-15T14:03:21.512Z"), envelope.occurredAt());
        assertEquals("350.00", envelope.payload().get("valorTotal").asText());
    }

    @Test
    void aceitaCorpoComoBufferDoVertx() {
        // Formato real entregue pelo conector RabbitMQ a um consumidor Message<?>.
        Buffer corpo = Buffer.buffer(envelopeValido().encode());

        assertEquals("OrcamentoGerado", Envelope.de(corpo).type());
    }

    @Test
    void aceitaCorpoComoTextoOuBytes() {
        String texto = envelopeValido().encode();

        assertEquals("OrcamentoGerado", Envelope.de(texto).type());
        assertEquals("OrcamentoGerado", Envelope.de(texto.getBytes(StandardCharsets.UTF_8)).type());
    }

    @Test
    void ignoraCamposDesconhecidos() {
        JsonObject json = envelopeValido().put("campoDoFuturo", "qualquer coisa");

        assertEquals("OrcamentoGerado", Envelope.de(json).type());
    }

    @Test
    void rejeitaEnvelopeSemMessageId() {
        JsonObject json = envelopeValido();
        json.remove("messageId");

        var erro = assertThrows(EnvelopeInvalidoException.class, () -> Envelope.de(json));
        assertTrue(erro.getMessage().contains("messageId"));
    }

    @Test
    void rejeitaEnvelopeSemType() {
        assertThrows(EnvelopeInvalidoException.class, () -> Envelope.de(envelopeValido().put("type", " ")));
        JsonObject semType = envelopeValido();
        semType.remove("type");
        assertThrows(EnvelopeInvalidoException.class, () -> Envelope.de(semType));
    }

    @Test
    void rejeitaEnvelopeSemCorrelationId() {
        JsonObject json = envelopeValido();
        json.remove("correlationId");

        var erro = assertThrows(EnvelopeInvalidoException.class, () -> Envelope.de(json));
        assertTrue(erro.getMessage().contains("correlationId"));
    }

    @Test
    void rejeitaCorpoQueNaoEJson() {
        assertThrows(EnvelopeInvalidoException.class, () -> Envelope.de("isto não é json"));
        assertThrows(EnvelopeInvalidoException.class, () -> Envelope.de(envelopeValido().put("messageId", "não-é-uuid")));
    }

    @Test
    void rejeitaTipoDeCorpoNaoSuportado() {
        assertThrows(EnvelopeInvalidoException.class, () -> Envelope.de(42));
        assertThrows(EnvelopeInvalidoException.class, () -> Envelope.de(null));
    }
}
