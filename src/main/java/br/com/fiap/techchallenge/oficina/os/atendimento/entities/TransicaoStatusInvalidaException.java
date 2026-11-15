package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;

public class TransicaoStatusInvalidaException extends DomainException {

    public TransicaoStatusInvalidaException(StatusOS atual, StatusOS desejado) {
        super("Transição de status inválida: " + atual + " → " + desejado);
    }

    public TransicaoStatusInvalidaException(String message) {
        super(message);
    }
}
