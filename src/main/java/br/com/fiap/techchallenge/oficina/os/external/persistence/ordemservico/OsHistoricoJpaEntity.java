package br.com.fiap.techchallenge.oficina.os.external.persistence.ordemservico;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Linha do histórico de status — só inserida, nunca alterada. */
@Entity
@Table(name = "os_historico")
public class OsHistoricoJpaEntity {

    // SEQUENCE, e não IDENTITY: com IDENTITY o Hibernate insere no persist(), antes da
    // OS (UUID atribuído, inserida só no flush) — e a FK os_id falharia.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "os_historico_seq")
    @SequenceGenerator(name = "os_historico_seq", sequenceName = "os_historico_id_seq", allocationSize = 1)
    @Column(name = "id")
    public Long id;

    @Column(name = "os_id", nullable = false)
    public UUID osId;

    @Column(name = "de", length = 32)
    public String de;

    @Column(name = "para", nullable = false, length = 32)
    public String para;

    @Column(name = "em", nullable = false)
    public OffsetDateTime em;

    @Column(name = "motivo", length = 120)
    public String motivo;
}
