package br.com.fiap.techchallenge.oficina.os.external.messaging;

import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.os.external.persistence.mensageria.OutboxJpaEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Relay do outbox (ADR 010): publica no RabbitMQ o que as transações gravaram.
 *
 * <ul>
 *   <li>{@code FOR UPDATE SKIP LOCKED} — várias réplicas rodam o relay sem publicar a
 *       mesma linha duas vezes;</li>
 *   <li>publica em ordem de gravação e <b>para no primeiro erro</b>, para não inverter a
 *       ordem dos comandos de uma mesma OS; o resto sai na próxima passada;</li>
 *   <li>só marca como publicado depois da confirmação do broker (publisher confirms);</li>
 *   <li>retoma o trace da transação original a partir do {@code traceparent} gravado.</li>
 * </ul>
 * Se o processo cair entre publicar e marcar, a mensagem sai de novo: o consumidor é
 * idempotente justamente para isso.
 */
@ApplicationScoped
public class RelayOutbox {

    private static final Logger LOG = Logger.getLogger(RelayOutbox.class);
    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};
    private static final TextMapGetter<Map<String, String>> LEITOR = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private final MutinyEmitter<String> emissor;
    private final EntityManager em;
    private final ObjectMapper json;
    private final MetricasGateway metricas;
    private final Clock relogio;
    private final int lote;

    public RelayOutbox(@Channel("comandos") MutinyEmitter<String> emissor, EntityManager em, ObjectMapper json,
                       MetricasGateway metricas, Clock relogio,
                       @ConfigProperty(name = "oficina.outbox.lote", defaultValue = "100") int lote) {
        this.emissor = emissor;
        this.em = em;
        this.json = json;
        this.metricas = metricas;
        this.relogio = relogio;
        this.lote = lote;
    }

    @Scheduled(every = "${oficina.outbox.intervalo:1s}", delayed = "${oficina.outbox.atraso-inicial:2s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void agendado() {
        publicarPendentes();
    }

    /** @return quantas mensagens foram publicadas nesta passada */
    public int publicarPendentes() {
        return QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<OutboxJpaEntity> pendentes = em.createNativeQuery("""
                            SELECT * FROM outbox
                            WHERE publicado_em IS NULL
                            ORDER BY id
                            LIMIT ?1
                            FOR UPDATE SKIP LOCKED
                            """, OutboxJpaEntity.class)
                    .setParameter(1, lote)
                    .getResultList();
            int publicadas = 0;
            for (OutboxJpaEntity linha : pendentes) {
                try {
                    publicar(linha);
                } catch (RuntimeException e) {
                    LOG.warnf("Falha ao publicar %s (messageId %s): %s — nova tentativa na próxima passada",
                            linha.tipo, linha.messageId, e.getMessage());
                    metricas.falhaIntegracao("rabbitmq-publicacao");
                    break;
                }
                linha.publicadoEm = OffsetDateTime.now(relogio);
                publicadas++;
            }
            return publicadas;
        });
    }

    private void publicar(OutboxJpaEntity linha) {
        OutgoingRabbitMQMetadata rabbit = OutgoingRabbitMQMetadata.builder()
                .withRoutingKey(linha.routingKey)
                .withMessageId(linha.messageId.toString())
                .withCorrelationId(linha.correlationId.toString())
                .withType(linha.tipo)
                .withContentType("application/json")
                .withDeliveryMode(2) // persistente: sobrevive a reinício do broker
                .build();
        Message<String> mensagem = Message.of(linha.envelope,
                Metadata.of(rabbit, TracingMetadata.withPrevious(contextoOriginal(linha))));
        emissor.sendMessageAndAwait(mensagem);
    }

    private Context contextoOriginal(OutboxJpaEntity linha) {
        try {
            Map<String, String> headers = json.readValue(linha.headers, MAPA);
            return W3CTraceContextPropagator.getInstance().extract(Context.root(), headers, LEITOR);
        } catch (Exception e) {
            return Context.root();
        }
    }
}
