package br.com.fiap.techchallenge.oficina.os.external.messaging;

import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.os.saga.controllers.SagaController;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.EfeitoSaga;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Porta de entrada dos eventos de Execução e Billing (fila {@code os.eventos}).
 *
 * <ul>
 *   <li>envelope ou payload malformado → {@code nack} → DLQ (reprocessar não conserta);</li>
 *   <li>tipo que o orquestrador não usa → {@code ack} e segue;</li>
 *   <li>falha técnica (banco, lock) → retentativa com backoff; esgotada, DLQ e alerta.</li>
 * </ul>
 * Erro de negócio do participante não chega aqui como exceção: chega como evento
 * ({@code OrcamentoNaoGerado}, {@code ReparoNaoLiberado}) e a saga compensa (ADR 010 §3).
 *
 * <p>{@code @Blocking}: o processamento abre transação JDBC e não pode rodar na event loop.
 */
@ApplicationScoped
public class ConsumidorEventos {

    private static final Logger LOG = Logger.getLogger(ConsumidorEventos.class);

    private final SagaController saga;
    private final MetricasGateway metricas;
    private final int tentativas;
    private final long backoffMs;

    public ConsumidorEventos(SagaController saga, MetricasGateway metricas,
                             @ConfigProperty(name = "oficina.mensageria.tentativas", defaultValue = "3") int tentativas,
                             @ConfigProperty(name = "oficina.mensageria.backoff-ms", defaultValue = "200") long backoffMs) {
        this.saga = saga;
        this.metricas = metricas;
        this.tentativas = Math.max(1, tentativas);
        this.backoffMs = backoffMs;
    }

    @Incoming("eventos")
    @Blocking
    public CompletionStage<Void> receber(Message<?> mensagem) {
        Envelope envelope;
        Optional<EventoSaga> evento;
        try {
            envelope = Envelope.de(mensagem.getPayload());
            evento = EventoSagaMapper.traduzir(envelope);
        } catch (EnvelopeInvalidoException e) {
            LOG.errorf("Evento rejeitado (vai para a DLQ): %s", e.getMessage());
            metricas.falhaIntegracao("rabbitmq-evento-invalido");
            return mensagem.nack(e);
        }

        MDC.put("correlationId", envelope.correlationId().toString());
        try {
            if (evento.isEmpty()) {
                LOG.infof("Evento %s sem tratamento no orquestrador — confirmado e ignorado", envelope.type());
                return mensagem.ack();
            }
            return processarComRetentativa(mensagem, envelope, evento.get());
        } finally {
            MDC.remove("correlationId");
        }
    }

    private CompletionStage<Void> processarComRetentativa(Message<?> mensagem, Envelope envelope, EventoSaga evento) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                EfeitoSaga efeito = saga.processarEvento(envelope.messageId(), envelope.correlationId(), evento);
                registrar(envelope, efeito);
                return mensagem.ack();
            } catch (RuntimeException e) {
                if (tentativa >= tentativas) {
                    LOG.errorf(e, "Evento %s (messageId %s) falhou %d vez(es) — vai para a DLQ",
                            envelope.type(), envelope.messageId(), tentativa);
                    metricas.falhaProcessamento("evento-" + envelope.type());
                    return mensagem.nack(e);
                }
                LOG.warnf("Evento %s falhou na tentativa %d/%d (%s) — nova tentativa",
                        envelope.type(), tentativa, tentativas, e.getMessage());
                esperar(backoffMs * (1L << (tentativa - 1)));
            }
        }
    }

    private static void registrar(Envelope envelope, EfeitoSaga efeito) {
        if (efeito.duplicada()) {
            LOG.infof("Evento %s (messageId %s) já processado — duplicata ignorada", envelope.type(), envelope.messageId());
        } else if (efeito.resultado().aplicado()) {
            LOG.infof("Evento %s aplicado: %s → saga %s", envelope.type(), efeito.resultado().descricao(),
                    efeito.saga().estado());
        } else {
            LOG.infof("Evento %s ignorado: %s", envelope.type(), efeito.resultado().descricao());
        }
    }

    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
