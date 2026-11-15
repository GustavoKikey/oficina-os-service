package br.com.fiap.techchallenge.oficina.os.saga.gateways;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ComandoSaga;

import java.util.List;
import java.util.UUID;

/**
 * Outbox transacional (ADR 010): grava os comandos na <b>mesma transação</b> do estado
 * da saga. Quem publica no RabbitMQ é o relay, depois do commit — se o processo cair
 * entre gravar e publicar, o comando sai na próxima execução do relay.
 */
public interface OutboxGateway {

    /**
     * @param causationId {@code messageId} da mensagem que causou estes comandos;
     *                    nulo quando a causa é uma ação de usuário ou um prazo
     */
    void enfileirar(List<ComandoSaga> comandos, OrdemServicoId correlationId, UUID causationId);
}
