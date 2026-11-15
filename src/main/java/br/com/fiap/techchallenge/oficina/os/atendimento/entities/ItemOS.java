package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;

import java.util.Objects;
import java.util.UUID;

/**
 * Cópia de um item do diagnóstico, como a Execução o registrou.
 *
 * <p>Value object só de leitura: o OS Service não altera itens — quem os cria é o
 * mecânico, na Execução (ADR 006). A cópia existe para o cliente ver o que está
 * aprovando e para compor o comando {@code GerarOrcamento}.
 *
 * @param referenciaId id do serviço de catálogo ou da peça, no banco da Execução
 */
public record ItemOS(TipoItem tipo, UUID referenciaId, String descricao,
                     int quantidade, Dinheiro valorUnitario) {

    public enum TipoItem { SERVICO, PECA }

    public ItemOS {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(referenciaId, "referenciaId");
        Objects.requireNonNull(valorUnitario, "valorUnitario");
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Descrição do item é obrigatória");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva: " + quantidade);
        }
    }

    public Dinheiro subtotal() {
        return valorUnitario.multiplicar(quantidade);
    }
}
