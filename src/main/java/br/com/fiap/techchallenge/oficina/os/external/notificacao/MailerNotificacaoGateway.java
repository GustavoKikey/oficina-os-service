package br.com.fiap.techchallenge.oficina.os.external.notificacao;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.NotificacaoGateway;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Adapter da porta {@link NotificacaoGateway} sobre o Quarkus Mailer (SMTP).
 *
 * <p>Em dev/test o mailer roda em modo <i>mock</i> (loga o e-mail em vez de enviar);
 * em produção as credenciais SMTP vêm de variáveis de ambiente. Envio é best-effort:
 * qualquer falha é logada e engolida para não derrubar a operação de negócio.
 *
 * <p>Na fase 4 o e-mail carrega o que o cliente precisa para agir: o valor do orçamento
 * a aprovar, o link de pagamento do Mercado Pago e o motivo de um cancelamento.
 */
@ApplicationScoped
public class MailerNotificacaoGateway implements NotificacaoGateway {

    private static final Logger LOG = Logger.getLogger(MailerNotificacaoGateway.class);

    private final Mailer mailer;
    private final MetricasGateway metricas;

    public MailerNotificacaoGateway(Mailer mailer, MetricasGateway metricas) {
        this.mailer = mailer;
        this.metricas = metricas;
    }

    @Override
    public void notificarMudancaStatus(OrdemServico os, Cliente cliente) {
        try {
            String assunto = "Oficina — OS %s: %s".formatted(os.id().valor(), os.status().descricao());
            mailer.send(Mail.withText(cliente.email(), assunto, corpo(os, cliente)));
            LOG.infof("Notificação de status enviada — OS %s → %s", os.id().valor(), os.status());
        } catch (Exception e) {
            metricas.falhaIntegracao("email");
            LOG.errorf(e, "Falha ao notificar o cliente sobre a OS %s (status %s)", os.id().valor(), os.status());
        }
    }

    static String corpo(OrdemServico os, Cliente cliente) {
        String nl = System.lineSeparator();
        StringBuilder corpo = new StringBuilder()
                .append("Olá, ").append(cliente.nome()).append('!').append(nl).append(nl)
                .append("Sua Ordem de Serviço ").append(os.id().valor())
                .append(" mudou de situação: ").append(os.status().descricao()).append('.').append(nl);
        if (os.orcamento() != null && os.status() == StatusOS.AGUARDANDO_APROVACAO) {
            corpo.append(nl).append("Valor do orçamento: R$ ").append(os.orcamento().valorTotal().valor())
                    .append(". Aprove ou recuse pela área do cliente.").append(nl);
        }
        if (os.pagamento().link() != null && os.status() == StatusOS.AGUARDANDO_PAGAMENTO) {
            corpo.append(nl).append("Pague pelo Mercado Pago: ").append(os.pagamento().link()).append(nl);
        }
        if (os.motivoCancelamento() != null) {
            corpo.append(nl).append("Motivo: ").append(os.motivoCancelamento().descricao()).append('.').append(nl);
        }
        corpo.append(nl).append("Acompanhe pela área do cliente: /cliente/ordens-servico/")
                .append(os.id().valor()).append(nl);
        return corpo.toString();
    }
}
