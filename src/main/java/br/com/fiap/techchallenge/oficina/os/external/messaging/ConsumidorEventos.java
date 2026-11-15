package br.com.fiap.techchallenge.oficina.os.external.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.concurrent.CompletionStage;

/**
 * Porta de entrada dos eventos de Execução e Billing (fila {@code os.eventos}).
 *
 * <p>Valida o envelope e põe o {@code correlationId} (= id da OS) no MDC, para que todo
 * log do processamento seja encontrável pela OS. Envelope inválido é rejeitado e vai
 * para a DLQ ({@code os.eventos.dlq}).
 *
 * <p>Esqueleto da etapa 2: o despacho para a saga (inbox + máquina de estados) entra
 * na etapa 3.
 */
@ApplicationScoped
public class ConsumidorEventos {

    private static final Logger LOG = Logger.getLogger(ConsumidorEventos.class);

    @Incoming("eventos")
    public CompletionStage<Void> receber(Message<?> mensagem) {
        Envelope envelope;
        try {
            envelope = Envelope.de(mensagem.getPayload());
        } catch (EnvelopeInvalidoException e) {
            LOG.errorf("Evento rejeitado (vai para a DLQ): %s", e.getMessage());
            return mensagem.nack(e);
        }

        MDC.put("correlationId", envelope.correlationId().toString());
        try {
            LOG.infof("Evento recebido: type=%s v%d messageId=%s source=%s",
                    envelope.type(), envelope.version(), envelope.messageId(), envelope.source());
            return mensagem.ack();
        } finally {
            MDC.remove("correlationId");
        }
    }
}
