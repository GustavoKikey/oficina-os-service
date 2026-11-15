package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Veiculo;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.VeiculoGateway;

public class BuscarVeiculoPorIdUseCase {

    private final VeiculoGateway repository;

    public BuscarVeiculoPorIdUseCase(VeiculoGateway repository) {
        this.repository = repository;
    }

    public Veiculo executar(VeiculoId id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(id));
    }
}
