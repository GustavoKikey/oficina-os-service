package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;

import java.util.List;

public class ListarClientesUseCase {

    private final ClienteGateway repository;

    public ListarClientesUseCase(ClienteGateway repository) {
        this.repository = repository;
    }

    public List<Cliente> executar() {
        return repository.listar();
    }
}
