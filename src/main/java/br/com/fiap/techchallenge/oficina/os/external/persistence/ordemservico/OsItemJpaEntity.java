package br.com.fiap.techchallenge.oficina.os.external.persistence.ordemservico;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Cópia de um item do diagnóstico (fonte: evento {@code DiagnosticoConcluido}). */
@Entity
@Table(name = "os_itens")
public class OsItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @ManyToOne
    @JoinColumn(name = "os_id", nullable = false)
    public OrdemServicoJpaEntity ordemServico;

    @Column(name = "posicao", nullable = false)
    public int posicao;

    @Column(name = "tipo", nullable = false, length = 8)
    public String tipo;

    @Column(name = "referencia_id", nullable = false)
    public UUID referenciaId;

    @Column(name = "descricao", nullable = false, length = 200)
    public String descricao;

    @Column(name = "quantidade", nullable = false)
    public int quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    public BigDecimal valorUnitario;
}
