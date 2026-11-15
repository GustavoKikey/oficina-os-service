package br.com.fiap.techchallenge.oficina.os.saga.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.AcessoNegadoAOrdemServicoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento.AprovadoPor;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.PrazosSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ResultadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.OutboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Decisão sobre o orçamento: aprovar dispara a cobrança no Billing; recusar dispara as
 * compensações (libera as peças, cancela o orçamento) e cancela a OS.
 *
 * <p>Quando quem decide é o cliente, a OS precisa ser <b>dele</b> — a conferência é feita
 * aqui, dentro da transação e com a saga travada (fase 3: o "cliente curioso" com o UUID
 * da OS de outra pessoa recebe 403).
 */
public class DecidirOrcamentoUseCase {

    private final SagaGateway sagas;
    private final OrdemServicoGateway ordens;
    private final OutboxGateway outbox;
    private final PrazosSaga prazos;
    private final Clock relogio;

    public DecidirOrcamentoUseCase(SagaGateway sagas, OrdemServicoGateway ordens, OutboxGateway outbox,
                                   PrazosSaga prazos, Clock relogio) {
        this.sagas = sagas;
        this.ordens = ordens;
        this.outbox = outbox;
        this.prazos = prazos;
        this.relogio = relogio;
    }

    /**
     * @param clienteSolicitante dono que decide pela área do cliente; nulo quando é o
     *                           atendente decidindo em nome do cliente
     */
    public record Input(OrdemServicoId osId, boolean aprovado, ClienteId clienteSolicitante) {}

    public EfeitoSaga executar(Input input) {
        SagaOrdemServico saga = sagas.buscarPorOsComLock(input.osId())
                .orElseThrow(() -> new OrdemServicoNaoEncontradaException(input.osId()));
        OrdemServico os = ordens.buscarPorId(input.osId())
                .orElseThrow(() -> new OrdemServicoNaoEncontradaException(input.osId()));
        if (input.clienteSolicitante() != null && !os.pertenceA(input.clienteSolicitante())) {
            throw new AcessoNegadoAOrdemServicoException(input.osId());
        }

        StatusOS statusAntes = os.status();
        EstadoSaga estadoAntes = saga.estado();
        OffsetDateTime agora = OffsetDateTime.now(relogio);
        AprovadoPor por = input.clienteSolicitante() != null ? AprovadoPor.CLIENTE : AprovadoPor.ATENDENTE;

        ResultadoSaga resultado = input.aprovado()
                ? saga.aprovarOrcamento(os, por, prazos, agora)
                : saga.recusarOrcamento(os, prazos, agora);

        ordens.salvar(os);
        sagas.salvar(saga);
        outbox.enfileirar(resultado.comandos(), os.id(), null);
        return new EfeitoSaga(os, saga, resultado, statusAntes, estadoAntes, false);
    }
}
