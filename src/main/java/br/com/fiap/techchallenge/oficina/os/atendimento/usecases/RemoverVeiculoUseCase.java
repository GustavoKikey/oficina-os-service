package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.VeiculoGateway;

public class RemoverVeiculoUseCase {

    private final VeiculoGateway repository;

    public RemoverVeiculoUseCase(VeiculoGateway repository) {
        this.repository = repository;
    }

    public void executar(VeiculoId id) {
        if (repository.buscarPorId(id).isEmpty()) {
            throw new VeiculoNaoEncontradoException(id);
        }
        repository.remover(id);
    }
}
