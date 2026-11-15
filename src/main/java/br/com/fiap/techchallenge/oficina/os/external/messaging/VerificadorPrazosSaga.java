package br.com.fiap.techchallenge.oficina.os.external.messaging;

import br.com.fiap.techchallenge.oficina.os.saga.controllers.SagaController;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Dispara a verificação de prazos da saga (saga.md §6): aprovação e pagamento vencidos,
 * participantes que não responderam. Com várias réplicas, cada saga é travada antes de
 * ser tratada — duas réplicas no mesmo ciclo não duplicam efeito.
 */
@ApplicationScoped
public class VerificadorPrazosSaga {

    private static final Logger LOG = Logger.getLogger(VerificadorPrazosSaga.class);

    private final SagaController saga;

    public VerificadorPrazosSaga(SagaController saga) {
        this.saga = saga;
    }

    @Scheduled(every = "${oficina.saga.verificacao-prazos:5s}", delayed = "${oficina.saga.atraso-inicial:10s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void verificar() {
        try {
            int tratadas = saga.verificarPrazos();
            if (tratadas > 0) {
                LOG.infof("Prazos da saga: %d saga(s) tratada(s)", tratadas);
            }
        } catch (RuntimeException e) {
            LOG.errorf(e, "Falha ao verificar prazos da saga — nova tentativa no próximo ciclo");
        }
    }
}
