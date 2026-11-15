package br.com.fiap.techchallenge.oficina.os.saga.gateways;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Leitura do outbox e do inbox de uma OS — o rastro da saga para operação. */
public interface RastroMensagensGateway {

    List<Mensagem> daOs(OrdemServicoId osId);

    /** @param publicadaEm só para enviadas; nulo enquanto o relay não publicou */
    record Mensagem(Direcao direcao, String tipo, UUID messageId, OffsetDateTime em, OffsetDateTime publicadaEm) {}

    enum Direcao { ENVIADA, RECEBIDA }
}
