package br.com.fiap.techchallenge.oficina.os.external.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope comum a todo comando e evento trocado pelo RabbitMQ.
 *
 * <p>Contrato em {@code docs/fase-4/contratos-mensagens.md} §2. Cada serviço tem a sua
 * cópia desta classe — não existe biblioteca compartilhada, para que os deploys não
 * fiquem acoplados. Campos desconhecidos são ignorados (regra 3 do contrato): é o que
 * permite evoluir o contrato sem deploy sincronizado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Envelope(
        UUID messageId,
        String type,
        int version,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        String source,
        JsonNode payload) {

    /**
     * Converte o corpo recebido do broker em envelope, validando os campos sem os
     * quais a mensagem não pode ser roteada nem deduplicada.
     *
     * <p>Com o consumidor declarado como {@code Message<?>}, o conector RabbitMQ entrega
     * o corpo cru como {@link Buffer} do Vert.x — é o formato real em produção. Texto,
     * bytes e {@link JsonObject} também são aceitos (conector em memória, testes, ou um
     * consumidor tipado no futuro).
     *
     * @throws EnvelopeInvalidoException se o corpo não for JSON ou faltar campo obrigatório
     */
    public static Envelope de(Object corpo) {
        Envelope envelope;
        try {
            envelope = paraJson(corpo).mapTo(Envelope.class);
        } catch (RuntimeException e) {
            throw new EnvelopeInvalidoException("corpo não é um envelope JSON válido: " + e.getMessage());
        }
        if (envelope.messageId() == null) {
            throw new EnvelopeInvalidoException("messageId ausente");
        }
        if (envelope.type() == null || envelope.type().isBlank()) {
            throw new EnvelopeInvalidoException("type ausente (messageId " + envelope.messageId() + ")");
        }
        if (envelope.correlationId() == null) {
            throw new EnvelopeInvalidoException("correlationId ausente (messageId " + envelope.messageId() + ")");
        }
        return envelope;
    }

    private static JsonObject paraJson(Object corpo) {
        if (corpo instanceof Buffer buffer) {
            return new JsonObject(buffer);
        }
        if (corpo instanceof JsonObject json) {
            return json;
        }
        if (corpo instanceof String texto) {
            return new JsonObject(texto);
        }
        if (corpo instanceof byte[] bytes) {
            return new JsonObject(Buffer.buffer(bytes));
        }
        throw new EnvelopeInvalidoException(
                "tipo de corpo não suportado: " + (corpo == null ? "null" : corpo.getClass().getName()));
    }
}
