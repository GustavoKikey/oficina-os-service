package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;

public class VeiculoJaCadastradoException extends DomainException {

    public VeiculoJaCadastradoException(String placa) {
        super("Já existe veículo cadastrado com a placa: " + placa);
    }
}
