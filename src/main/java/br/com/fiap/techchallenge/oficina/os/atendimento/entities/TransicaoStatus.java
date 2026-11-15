package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Uma linha do histórico da OS: de onde veio, para onde foi, quando e por quê.
 *
 * @param de     nulo na abertura
 * @param motivo texto curto para o histórico (ex.: "Pagamento confirmado"); pode ser nulo
 */
public record TransicaoStatus(StatusOS de, StatusOS para, OffsetDateTime em, String motivo) {

    public TransicaoStatus {
        Objects.requireNonNull(para, "para");
        Objects.requireNonNull(em, "em");
    }
}
