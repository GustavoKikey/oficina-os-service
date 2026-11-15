package br.com.fiap.techchallenge.oficina.os.saga.controllers;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.NotificarStatusOSUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.EfeitoSaga;
import br.com.fiap.techchallenge.oficina.os.shared.usecases.ExecutorTransacional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * O que acontece <b>depois do commit</b> de um passo da saga: e-mail ao cliente e
 * métricas. Nenhum dos dois tem rollback — se rodassem dentro da transação do passo,
 * um rollback deixaria o cliente avisado de algo que não aconteceu.
 *
 * <p>Compartilhado pela API (decisões de pessoas), pelo consumidor de eventos e pelo
 * verificador de prazos. Duas regras, ambas descobertas pelos testes de integração:
 * <ul>
 *   <li>a notificação lê o cliente no banco <b>numa transação própria</b> — no consumidor
 *       de mensagens não há request context, e uma leitura solta falharia;</li>
 *   <li>nada aqui propaga exceção: o passo já foi gravado, e uma falha de e-mail não pode
 *       fazer o consumidor reprocessar a mensagem (que o inbox descartaria, perdendo de
 *       vez as métricas).</li>
 * </ul>
 */
public class PosCommitSaga {

    private static final System.Logger LOG = System.getLogger(PosCommitSaga.class.getName());

    private final NotificarStatusOSUseCase notificar;
    private final MetricasGateway metricas;
    private final ExecutorTransacional tx;

    public PosCommitSaga(NotificarStatusOSUseCase notificar, MetricasGateway metricas, ExecutorTransacional tx) {
        this.notificar = notificar;
        this.metricas = metricas;
        this.tx = tx;
    }

    /**
     * @param avisarMesmoSemMudarStatus o link de pagamento chega sem mudar o status, mas
     *                                  é exatamente o que o cliente precisa receber
     */
    public void aplicar(EfeitoSaga efeito, boolean avisarMesmoSemMudarStatus) {
        if (efeito.duplicada() || efeito.os() == null || efeito.resultado() == null
                || !efeito.resultado().aplicado()) {
            return;
        }
        OrdemServico os = efeito.os();
        if (efeito.statusMudou() || avisarMesmoSemMudarStatus) {
            notificarComSeguranca(os);
        }
        if (efeito.statusMudou()) {
            registrarFaseConcluida(os);
        }
        if (efeito.entrouEm(EstadoSaga.CONCLUIDA)) {
            metricas.sagaConcluida();
        }
        if (efeito.entrouEm(EstadoSaga.COMPENSANDO)) {
            metricas.sagaCompensada(os.motivoCancelamento() == null ? null : os.motivoCancelamento().name());
        }
        if (efeito.resultado().falhou()) {
            metricas.sagaFalhou(efeito.estadoAntes() == null ? null : efeito.estadoAntes().name());
        }
    }

    public void notificarComSeguranca(OrdemServico os) {
        try {
            tx.emTransacao(() -> {
                notificar.executar(os);
                return null;
            });
        } catch (RuntimeException e) {
            metricas.falhaIntegracao("notificacao");
            LOG.log(System.Logger.Level.WARNING,
                    "Falha ao notificar o cliente da OS " + os.id() + " — passo da saga já gravado", e);
        }
    }

    /** Mesmo critério da fase 3: cada transição fecha exatamente uma fase medida. */
    public void registrarFaseConcluida(OrdemServico os) {
        switch (os.status()) {
            case EM_EXECUCAO -> registrar(StatusOS.EM_DIAGNOSTICO, os.diagnosticoIniciadoEm(), os.execucaoIniciadaEm());
            case FINALIZADA -> registrar(StatusOS.EM_EXECUCAO, os.execucaoIniciadaEm(), os.finalizadaEm());
            case ENTREGUE -> registrar(StatusOS.FINALIZADA, os.finalizadaEm(), os.entregueEm());
            default -> { /* demais status não encerram uma fase medida */ }
        }
    }

    private void registrar(StatusOS fase, OffsetDateTime inicio, OffsetDateTime fim) {
        if (inicio != null && fim != null) {
            metricas.faseConcluida(fase.descricao(), Duration.between(inicio, fim));
        }
    }
}
