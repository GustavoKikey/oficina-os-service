package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;

import java.time.Clock;
import java.time.OffsetDateTime;

/** Entrega do veículo (FINALIZADA → ENTREGUE). Fora da saga: não envolve outro serviço. */
public class EntregarVeiculoUseCase {

    private final OrdemServicoGateway repository;
    private final Clock relogio;

    public EntregarVeiculoUseCase(OrdemServicoGateway repository, Clock relogio) {
        this.repository = repository;
        this.relogio = relogio;
    }

    public OrdemServico executar(OrdemServicoId id) {
        OrdemServico os = repository.buscarPorId(id)
                .orElseThrow(() -> new OrdemServicoNaoEncontradaException(id));
        os.entregar(OffsetDateTime.now(relogio));
        return repository.salvar(os);
    }
}
