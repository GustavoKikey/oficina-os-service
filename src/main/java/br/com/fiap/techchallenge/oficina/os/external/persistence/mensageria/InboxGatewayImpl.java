package br.com.fiap.techchallenge.oficina.os.external.persistence.mensageria;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.InboxGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.UUID;

@ApplicationScoped
public class InboxGatewayImpl implements InboxGateway {

    private final EntityManager em;

    public InboxGatewayImpl(EntityManager em) {
        this.em = em;
    }

    /**
     * {@code ON CONFLICT DO NOTHING} em vez de capturar a violação de chave: uma
     * exceção de constraint marcaria a transação inteira para rollback.
     */
    @Override
    public boolean registrarSeNova(UUID messageId, String tipo, OrdemServicoId correlationId) {
        int inseridas = em.createNativeQuery("""
                        INSERT INTO mensagens_processadas (message_id, tipo, correlation_id)
                        VALUES (?1, ?2, ?3)
                        ON CONFLICT (message_id) DO NOTHING
                        """)
                .setParameter(1, messageId)
                .setParameter(2, tipo)
                .setParameter(3, correlationId.valor())
                .executeUpdate();
        return inseridas == 1;
    }
}
