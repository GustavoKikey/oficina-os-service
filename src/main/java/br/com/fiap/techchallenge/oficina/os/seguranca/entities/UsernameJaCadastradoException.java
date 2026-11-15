package br.com.fiap.techchallenge.oficina.os.seguranca.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;

public class UsernameJaCadastradoException extends DomainException {

    public UsernameJaCadastradoException(String username) {
        super("Username já cadastrado: " + username);
    }
}
