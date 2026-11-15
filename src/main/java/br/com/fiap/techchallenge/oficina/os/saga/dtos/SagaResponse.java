package br.com.fiap.techchallenge.oficina.os.saga.dtos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Estado de uma saga para operação ({@code GET /admin/sagas/{osId}}).
 *
 * @param comandosPendentes comandos cuja resposta a saga aguarda — reenviados no timeout
 * @param mensagens         o que foi trocado com os participantes, em ordem cronológica
 */
public record SagaResponse(
        UUID osId,
        String estado,
        String estadoAntesDaFalha,
        String statusOS,
        OffsetDateTime prazo,
        int tentativas,
        List<ComandoView> comandosPendentes,
        String motivoFalha,
        OffsetDateTime iniciadaEm,
        OffsetDateTime atualizadaEm,
        List<MensagemView> mensagens
) {
    public record ComandoView(String tipo, String destino) {}

    /** @param direcao {@code ENVIADA} (comando no outbox) ou {@code RECEBIDA} (evento no inbox) */
    public record MensagemView(String direcao, String tipo, UUID messageId, OffsetDateTime em,
                               OffsetDateTime publicadaEm) {}
}
