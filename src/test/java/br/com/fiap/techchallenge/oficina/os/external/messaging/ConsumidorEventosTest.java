package br.com.fiap.techchallenge.oficina.os.external.messaging;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumidorEventosTest {

    private final ConsumidorEventos consumidor = new ConsumidorEventos();

    private final AtomicBoolean confirmada = new AtomicBoolean();
    private final AtomicReference<Throwable> rejeitada = new AtomicReference<>();

    private Message<Object> mensagem(Object corpo) {
        return Message.of(corpo,
                () -> {
                    confirmada.set(true);
                    return CompletableFuture.completedFuture(null);
                },
                erro -> {
                    rejeitada.set(erro);
                    return CompletableFuture.completedFuture(null);
                });
    }

    @Test
    void confirmaEventoComEnvelopeValido() {
        consumidor.receber(mensagem(EnvelopeTest.envelopeValido())).toCompletableFuture().join();

        assertTrue(confirmada.get());
        assertNull(rejeitada.get());
    }

    @Test
    void rejeitaEventoComEnvelopeInvalidoParaIrADlq() {
        consumidor.receber(mensagem("{\"type\":\"OrcamentoGerado\"}")).toCompletableFuture().join();

        assertFalse(confirmada.get());
        assertInstanceOf(EnvelopeInvalidoException.class, rejeitada.get());
    }
}
