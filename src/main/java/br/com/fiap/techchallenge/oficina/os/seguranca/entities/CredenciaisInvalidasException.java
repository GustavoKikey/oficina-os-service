package br.com.fiap.techchallenge.oficina.os.seguranca.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;

/**
 * Falha de autenticação — username inexistente, senha errada ou usuário inativo.
 * A mensagem é deliberadamente genérica para não revelar qual desses casos ocorreu.
 */
public class CredenciaisInvalidasException extends DomainException {

    public CredenciaisInvalidasException() {
        super("Credenciais inválidas");
    }
}
