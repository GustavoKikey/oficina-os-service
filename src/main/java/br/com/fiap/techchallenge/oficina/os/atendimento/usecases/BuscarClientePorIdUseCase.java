package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;

public class BuscarClientePorIdUseCase {

    private final ClienteGateway repository;

    public BuscarClientePorIdUseCase(ClienteGateway repository) {
        this.repository = repository;
    }

    public Cliente executar(ClienteId id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException(id));
    }
}
