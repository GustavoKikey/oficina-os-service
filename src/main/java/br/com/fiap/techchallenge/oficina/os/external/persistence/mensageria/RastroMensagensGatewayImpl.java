package br.com.fiap.techchallenge.oficina.os.external.persistence.mensageria;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.RastroMensagensGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RastroMensagensGatewayImpl implements RastroMensagensGateway {

    private final EntityManager em;

    public RastroMensagensGatewayImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Mensagem> daOs(OrdemServicoId osId) {
        List<Mensagem> mensagens = new ArrayList<>();
        em.createQuery("from OutboxJpaEntity where correlationId = :os order by id", OutboxJpaEntity.class)
                .setParameter("os", osId.valor())
                .getResultList()
                .forEach(o -> mensagens.add(new Mensagem(Direcao.ENVIADA, o.tipo, o.messageId, o.criadoEm, o.publicadoEm)));

        @SuppressWarnings("unchecked")
        List<Object[]> recebidas = em.createNativeQuery("""
                        SELECT message_id, tipo, processada_em FROM mensagens_processadas
                        WHERE correlation_id = ?1 ORDER BY processada_em
                        """)
                .setParameter(1, osId.valor())
                .getResultList();
        for (Object[] r : recebidas) {
            mensagens.add(new Mensagem(Direcao.RECEBIDA, (String) r[1], (UUID) r[0], instante(r[2]), null));
        }
        mensagens.sort(Comparator.comparing(Mensagem::em));
        return mensagens;
    }

    /** O driver devolve {@code timestamptz} como Instant ou OffsetDateTime conforme a versão. */
    private static OffsetDateTime instante(Object valor) {
        if (valor instanceof OffsetDateTime odt) {
            return odt;
        }
        if (valor instanceof java.time.Instant i) {
            return i.atOffset(ZoneOffset.UTC);
        }
        return ((java.sql.Timestamp) valor).toInstant().atOffset(ZoneOffset.UTC);
    }
}
