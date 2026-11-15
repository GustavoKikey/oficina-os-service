package br.com.fiap.techchallenge.oficina.os.saga.entities;

import java.time.Duration;
import java.util.Objects;

/**
 * Prazos da saga ({@code docs/fase-4/saga.md} §6). Configuráveis por ambiente — na
 * demonstração são reduzidos para minutos.
 *
 * @param resposta      quanto esperar a resposta de um comando antes de reenviar
 * @param aprovacao     quanto o cliente tem para decidir o orçamento
 * @param pagamento     margem do orquestrador para o pagamento (o link do Mercado Pago
 *                      expira um pouco antes, e o Billing avisa)
 * @param maxTentativas reenvios por passo antes de declarar FALHA
 */
public record PrazosSaga(Duration resposta, Duration aprovacao, Duration pagamento, int maxTentativas) {

    public static final PrazosSaga PADRAO =
            new PrazosSaga(Duration.ofSeconds(30), Duration.ofHours(72), Duration.ofMinutes(35), 3);

    public PrazosSaga {
        Objects.requireNonNull(resposta, "resposta");
        Objects.requireNonNull(aprovacao, "aprovacao");
        Objects.requireNonNull(pagamento, "pagamento");
        if (maxTentativas < 0) {
            throw new IllegalArgumentException("maxTentativas não pode ser negativo");
        }
    }

    /** Backoff exponencial: 30 s, 60 s, 120 s… a cada reenvio do mesmo passo. */
    Duration respostaNaTentativa(int tentativa) {
        return resposta.multipliedBy(1L << Math.min(tentativa, 10));
    }
}
