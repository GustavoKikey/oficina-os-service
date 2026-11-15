package br.com.fiap.techchallenge.oficina.os.saga.gateways;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Persistência da saga — uma linha por OS. */
public interface SagaGateway {

    void salvar(SagaOrdemServico saga);

    Optional<SagaOrdemServico> buscarPorOs(OrdemServicoId osId);

    /**
     * Busca travando a linha ({@code SELECT … FOR UPDATE}) até o fim da transação:
     * dois eventos da mesma OS, consumidos por réplicas diferentes, são aplicados um
     * depois do outro — nunca sobre o mesmo estado lido.
     */
    Optional<SagaOrdemServico> buscarPorOsComLock(OrdemServicoId osId);

    /** OS cujas sagas têm prazo vencido em {@code agora}, as mais antigas primeiro. */
    List<OrdemServicoId> listarComPrazoVencido(OffsetDateTime agora, int limite);
}
