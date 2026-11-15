package br.com.fiap.techchallenge.oficina.os.external.persistence.ordemservico;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.TransicaoStatus;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OrdemServicoGatewayImpl implements OrdemServicoGateway, PanacheRepositoryBase<OrdemServicoJpaEntity, UUID> {

    private final EntityManager em;

    public OrdemServicoGatewayImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public OrdemServico salvar(OrdemServico os) {
        OrdemServicoJpaEntity existente = findById(os.id().valor());
        OrdemServicoJpaEntity entity = OrdemServicoJpaMapper.toEntity(os, existente);
        if (existente == null) {
            persist(entity);
        }
        for (TransicaoStatus t : os.novasTransicoes()) {
            em.persist(OrdemServicoJpaMapper.toEntity(os.id(), t));
        }
        os.transicoesPersistidas();
        return os;
    }

    @Override
    public Optional<OrdemServico> buscarPorId(OrdemServicoId id) {
        return findByIdOptional(id.valor()).map(OrdemServicoJpaMapper::toDomain);
    }

    @Override
    public List<OrdemServico> listar(Filtro filtro) {
        StringBuilder query = new StringBuilder("1=1");
        Parameters params = new Parameters();
        if (filtro.status() != null) {
            query.append(" and status = :status");
            params.and("status", filtro.status().name());
        }
        if (filtro.clienteId() != null) {
            query.append(" and clienteId = :clienteId");
            params.and("clienteId", filtro.clienteId().valor());
        }
        if (filtro.veiculoId() != null) {
            query.append(" and veiculoId = :veiculoId");
            params.and("veiculoId", filtro.veiculoId().valor());
        }
        return find(query.toString(), params).list().stream().map(OrdemServicoJpaMapper::toDomain).toList();
    }

    @Override
    public List<TransicaoStatus> historico(OrdemServicoId id) {
        List<OsHistoricoJpaEntity> linhas = em.createQuery(
                        "from OsHistoricoJpaEntity where osId = :os order by id", OsHistoricoJpaEntity.class)
                .setParameter("os", id.valor())
                .getResultList();
        return OrdemServicoJpaMapper.toDomain(linhas);
    }
}
