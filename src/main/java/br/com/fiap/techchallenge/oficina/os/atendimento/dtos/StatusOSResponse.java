package br.com.fiap.techchallenge.oficina.os.atendimento.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consulta de status com descrição amigável.
 *
 * @param linkPagamento preenchido em AGUARDANDO_PAGAMENTO, quando o Billing já criou a
 *                      cobrança — é por ele que o cliente paga
 */
public record StatusOSResponse(
        UUID id,
        String status,
        String descricao,
        OffsetDateTime atualizadoEm,
        String linkPagamento
) {}
