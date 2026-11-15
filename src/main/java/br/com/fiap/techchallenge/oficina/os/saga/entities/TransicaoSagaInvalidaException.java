package br.com.fiap.techchallenge.oficina.os.saga.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;

/**
 * Ação de usuário que não cabe no estado atual da saga — por exemplo, aprovar duas
 * vezes o mesmo orçamento (duplo clique). Vira {@code 409 Conflict}, sem efeito.
 */
public class TransicaoSagaInvalidaException extends DomainException {

    public TransicaoSagaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
