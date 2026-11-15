package br.com.fiap.techchallenge.oficina.os.external.messaging;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.os.suporte.Fixtures;
import br.com.fiap.techchallenge.oficina.os.saga.controllers.SagaController;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ResultadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.EfeitoSaga;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumidorEventosTest {

    private SagaController saga;
    private MetricasGateway metricas;
    private ConsumidorEventos consumidor;

    private final AtomicBoolean confirmada = new AtomicBoolean();
    private final AtomicReference<Throwable> rejeitada = new AtomicReference<>();

    @BeforeEach
    void preparar() {
        saga = mock(SagaController.class);
        metricas = mock(MetricasGateway.class);
        consumidor = new ConsumidorEventos(saga, metricas, 3, 0);
    }

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

    private static JsonObject envelope(String tipo, JsonObject payload) {
        return EnvelopeTest.envelopeValido().put("type", tipo).put("payload", payload);
    }

    private static EfeitoSaga efeito(boolean aplicado) {
        SagaOrdemServico s = SagaOrdemServico.reconstituir(OrdemServicoId.novo(), EstadoSaga.AGUARDANDO_APROVACAO,
                null, null, 0, List.of(), null, Fixtures.AGORA, Fixtures.AGORA, 0);
        return new EfeitoSaga(null, s, new ResultadoSaga(aplicado, List.of(), false, "teste"), null, null, false);
    }

    @Test
    void entregaOEventoTipadoASagaEConfirma() {
        when(saga.processarEvento(any(), any(), any())).thenReturn(efeito(true));
        JsonObject corpo = envelope("OrcamentoGerado", new JsonObject()
                .put("orcamentoId", UUID.randomUUID().toString())
                .put("valorTotal", "310.00")
                .put("geradoEm", "2026-11-15T14:41:00Z"));

        consumidor.receber(mensagem(Buffer.buffer(corpo.encode()))).toCompletableFuture().join();

        ArgumentCaptor<EventoSaga> evento = ArgumentCaptor.forClass(EventoSaga.class);
        verify(saga).processarEvento(eq(UUID.fromString(corpo.getString("messageId"))),
                eq(UUID.fromString(corpo.getString("correlationId"))), evento.capture());
        assertInstanceOf(EventoSaga.OrcamentoGerado.class, evento.getValue());
        assertTrue(confirmada.get());
        assertNull(rejeitada.get());
    }

    @Test
    void eventoIgnoradoOuDuplicadoTambemEConfirmado() {
        when(saga.processarEvento(any(), any(), any()))
                .thenReturn(efeito(false))
                .thenReturn(new EfeitoSaga(null, null, null, null, null, true));

        consumidor.receber(mensagem(envelope("OrdemExecucaoAberta", new JsonObject()))).toCompletableFuture().join();
        consumidor.receber(mensagem(envelope("OrdemExecucaoAberta", new JsonObject()))).toCompletableFuture().join();

        assertTrue(confirmada.get());
        assertNull(rejeitada.get());
    }

    @Test
    void tipoSemTratamentoEConfirmadoSemChamarASaga() {
        consumidor.receber(mensagem(envelope("AlgoQueOOrquestradorNaoUsa", new JsonObject())))
                .toCompletableFuture().join();

        verify(saga, never()).processarEvento(any(), any(), any());
        assertTrue(confirmada.get());
    }

    @Test
    void envelopeInvalidoVaiParaADlq() {
        consumidor.receber(mensagem("{\"type\":\"OrcamentoGerado\"}")).toCompletableFuture().join();

        assertFalse(confirmada.get());
        assertInstanceOf(EnvelopeInvalidoException.class, rejeitada.get());
        verify(metricas).falhaIntegracao("rabbitmq-evento-invalido");
    }

    @Test
    void payloadSemCampoObrigatorioVaiParaADlq() {
        consumidor.receber(mensagem(envelope("OrcamentoGerado", new JsonObject()))).toCompletableFuture().join();

        assertInstanceOf(EnvelopeInvalidoException.class, rejeitada.get());
        verify(saga, never()).processarEvento(any(), any(), any());
    }

    @Test
    void falhaTecnicaPassageiraERetentada() {
        when(saga.processarEvento(any(), any(), any()))
                .thenThrow(new IllegalStateException("deadlock"))
                .thenReturn(efeito(true));

        consumidor.receber(mensagem(envelope("OrdemExecucaoAberta", new JsonObject()))).toCompletableFuture().join();

        verify(saga, times(2)).processarEvento(any(), any(), any());
        assertTrue(confirmada.get());
    }

    @Test
    void falhaTecnicaPersistenteEsgotaAsTentativasEVaiParaADlq() {
        when(saga.processarEvento(any(), any(), any())).thenThrow(new IllegalStateException("banco fora"));

        consumidor.receber(mensagem(envelope("OrdemExecucaoAberta", new JsonObject()))).toCompletableFuture().join();

        verify(saga, times(3)).processarEvento(any(), any(), any());
        assertInstanceOf(IllegalStateException.class, rejeitada.get());
        verify(metricas).falhaProcessamento("evento-OrdemExecucaoAberta");
    }
}
