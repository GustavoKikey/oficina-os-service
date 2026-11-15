package br.com.fiap.techchallenge.oficina.os.saga.gateways;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;

import java.util.UUID;

/**
 * Inbox (ADR 010): deduplica eventos pelo {@code messageId}, na mesma transação do
 * efeito. Se o efeito sofrer rollback, o registro também sai — e a mensagem pode ser
 * reprocessada.
 */
public interface InboxGateway {

    /** @return {@code false} se a mensagem já foi processada (duplicata — ignorar) */
    boolean registrarSeNova(UUID messageId, String tipo, OrdemServicoId correlationId);
}
