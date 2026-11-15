package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.TransicaoStatus;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;

import java.util.List;

/** "Consulta de status e histórico" — exigência do enunciado para o OS Service. */
public class ConsultarHistoricoOSUseCase {

    private final OrdemServicoGateway repository;

    public ConsultarHistoricoOSUseCase(OrdemServicoGateway repository) {
        this.repository = repository;
    }

    public List<TransicaoStatus> executar(OrdemServicoId id) {
        if (repository.buscarPorId(id).isEmpty()) {
            throw new OrdemServicoNaoEncontradaException(id);
        }
        return repository.historico(id);
    }
}
