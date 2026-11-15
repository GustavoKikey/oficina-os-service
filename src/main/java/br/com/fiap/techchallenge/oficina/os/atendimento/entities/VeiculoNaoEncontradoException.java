package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;

public class VeiculoNaoEncontradoException extends DomainException {

    public VeiculoNaoEncontradoException(VeiculoId id) {
        super("Veículo não encontrado: " + id);
    }

    public VeiculoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
