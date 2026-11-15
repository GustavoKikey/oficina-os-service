package br.com.fiap.techchallenge.oficina.os.external.persistence.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sagas")
public class SagaJpaEntity {

    @Id
    @Column(name = "os_id", nullable = false, updatable = false)
    public UUID osId;

    @Column(name = "estado", nullable = false, length = 32)
    public String estado;

    @Column(name = "estado_antes_falha", length = 32)
    public String estadoAntesDaFalha;

    @Column(name = "prazo")
    public OffsetDateTime prazo;

    @Column(name = "tentativas", nullable = false)
    public int tentativas;

    /** Lista de {@code ComandoSaga} em JSON — reenviada no timeout. */
    @Column(name = "comandos_pendentes", nullable = false)
    public String comandosPendentes;

    @Column(name = "motivo_falha", length = 300)
    public String motivoFalha;

    @Column(name = "iniciada_em", nullable = false)
    public OffsetDateTime iniciadaEm;

    @Column(name = "atualizada_em", nullable = false)
    public OffsetDateTime atualizadaEm;

    /** Rede de segurança além do lock pessimista: atualização concorrente não passa em silêncio. */
    @Version
    @Column(name = "versao", nullable = false)
    public long versao;
}
