package br.com.fiap.techchallenge.oficina.os.seguranca.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;

public class UsuarioNaoEncontradoException extends DomainException {

    public UsuarioNaoEncontradoException(String username) {
        super("Usuário não encontrado: " + username);
    }

    public UsuarioNaoEncontradoException(UsuarioId id) {
        super("Usuário não encontrado: " + id);
    }
}
