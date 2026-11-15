package br.com.fiap.techchallenge.oficina.os.external.persistence.mensageria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Mensagem a publicar (ADR 010). O {@code id} sequencial dá a ordem de publicação. */
@Entity
@Table(name = "outbox")
public class OutboxJpaEntity {

    // SEQUENCE pelo mesmo motivo do histórico: inserir só no flush, depois da OS e da saga.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_seq")
    @SequenceGenerator(name = "outbox_seq", sequenceName = "outbox_id_seq", allocationSize = 1)
    @Column(name = "id")
    public Long id;

    @Column(name = "message_id", nullable = false, unique = true)
    public UUID messageId;

    @Column(name = "tipo", nullable = false, length = 64)
    public String tipo;

    @Column(name = "routing_key", nullable = false, length = 64)
    public String routingKey;

    @Column(name = "correlation_id", nullable = false)
    public UUID correlationId;

    /** Envelope completo em JSON, exatamente como vai para o broker. */
    @Column(name = "envelope", nullable = false)
    public String envelope;

    /** {@code traceparent}/{@code tracestate} capturados na transação, em JSON. */
    @Column(name = "headers", nullable = false)
    public String headers;

    @Column(name = "criado_em", nullable = false)
    public OffsetDateTime criadoEm;

    @Column(name = "publicado_em")
    public OffsetDateTime publicadoEm;
}
