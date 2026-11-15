package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * O que o OS Service sabe do orçamento: identificação, valor e a decisão do cliente.
 * O orçamento completo (itens, histórico, pagamento) vive no Billing (ADR 006).
 *
 * @param aprovadoEm  nulo enquanto o cliente não decidiu
 * @param aprovadoPor nulo enquanto o cliente não decidiu
 */
public record ResumoOrcamento(UUID orcamentoId, Dinheiro valorTotal, OffsetDateTime geradoEm,
                              OffsetDateTime aprovadoEm, AprovadoPor aprovadoPor) {

    /** Quem registrou a aprovação: o próprio cliente (área do cliente) ou o atendente por ele. */
    public enum AprovadoPor { CLIENTE, ATENDENTE }

    public ResumoOrcamento {
        Objects.requireNonNull(orcamentoId, "orcamentoId");
        Objects.requireNonNull(valorTotal, "valorTotal");
        Objects.requireNonNull(geradoEm, "geradoEm");
        if ((aprovadoEm == null) != (aprovadoPor == null)) {
            throw new IllegalArgumentException("aprovadoEm e aprovadoPor andam juntos");
        }
    }

    public static ResumoOrcamento gerado(UUID orcamentoId, Dinheiro valorTotal, OffsetDateTime geradoEm) {
        return new ResumoOrcamento(orcamentoId, valorTotal, geradoEm, null, null);
    }

    ResumoOrcamento aprovado(AprovadoPor por, OffsetDateTime em) {
        return new ResumoOrcamento(orcamentoId, valorTotal, geradoEm,
                Objects.requireNonNull(em, "em"), Objects.requireNonNull(por, "por"));
    }

    public boolean aprovado() {
        return aprovadoEm != null;
    }
}
