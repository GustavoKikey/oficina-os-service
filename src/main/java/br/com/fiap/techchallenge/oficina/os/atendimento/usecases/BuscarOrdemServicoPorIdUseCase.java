package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;

public class BuscarOrdemServicoPorIdUseCase {

    private final OrdemServicoGateway repository;

    public BuscarOrdemServicoPorIdUseCase(OrdemServicoGateway repository) {
        this.repository = repository;
    }

    public OrdemServico executar(OrdemServicoId id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradaException(id));
    }
}
