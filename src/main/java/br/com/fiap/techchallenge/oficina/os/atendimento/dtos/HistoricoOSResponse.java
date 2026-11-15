package br.com.fiap.techchallenge.oficina.os.atendimento.dtos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HistoricoOSResponse(UUID osId, String statusAtual, List<TransicaoView> transicoes) {

    /** @param de nulo na abertura da OS */
    public record TransicaoView(String de, String para, String descricao, OffsetDateTime em, String motivo) {}
}
