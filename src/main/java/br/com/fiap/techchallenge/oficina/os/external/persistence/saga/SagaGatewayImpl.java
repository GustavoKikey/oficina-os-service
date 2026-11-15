package br.com.fiap.techchallenge.oficina.os.external.persistence.saga;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ComandoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EstadoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SagaGatewayImpl implements SagaGateway, PanacheRepositoryBase<SagaJpaEntity, UUID> {

    private static final TypeReference<List<ComandoJson>> LISTA_DE_COMANDOS = new TypeReference<>() {};

    private final ObjectMapper json;

    public SagaGatewayImpl(ObjectMapper json) {
        this.json = json;
    }

    /** Forma serializada de {@link ComandoSaga} — o domínio não conhece Jackson. */
    record ComandoJson(String tipo, String destino, Map<String, Object> payload) {}

    @Override
    public void salvar(SagaOrdemServico saga) {
        SagaJpaEntity e = findById(saga.osId().valor());
        boolean nova = e == null;
        if (nova) {
            e = new SagaJpaEntity();
            e.osId = saga.osId().valor();
        }
        e.estado = saga.estado().name();
        e.estadoAntesDaFalha = saga.estadoAntesDaFalha() == null ? null : saga.estadoAntesDaFalha().name();
        e.prazo = saga.prazo();
        e.tentativas = saga.tentativas();
        e.comandosPendentes = escrever(saga.comandosPendentes());
        e.motivoFalha = saga.motivoFalha();
        e.iniciadaEm = saga.iniciadaEm();
        e.atualizadaEm = saga.atualizadaEm();
        if (nova) {
            persist(e);
        }
    }

    @Override
    public Optional<SagaOrdemServico> buscarPorOs(OrdemServicoId osId) {
        return findByIdOptional(osId.valor()).map(this::toDomain);
    }

    @Override
    public Optional<SagaOrdemServico> buscarPorOsComLock(OrdemServicoId osId) {
        return Optional.ofNullable(findById(osId.valor(), LockModeType.PESSIMISTIC_WRITE)).map(this::toDomain);
    }

    @Override
    public List<OrdemServicoId> listarComPrazoVencido(OffsetDateTime agora, int limite) {
        return find("prazo <= ?1", Sort.ascending("prazo"), agora).page(0, limite).list().stream()
                .map(e -> OrdemServicoId.de(e.osId))
                .toList();
    }

    private SagaOrdemServico toDomain(SagaJpaEntity e) {
        return SagaOrdemServico.reconstituir(
                OrdemServicoId.de(e.osId),
                EstadoSaga.valueOf(e.estado),
                e.estadoAntesDaFalha == null ? null : EstadoSaga.valueOf(e.estadoAntesDaFalha),
                e.prazo,
                e.tentativas,
                ler(e.comandosPendentes),
                e.motivoFalha,
                e.iniciadaEm,
                e.atualizadaEm,
                e.versao);
    }

    private String escrever(List<ComandoSaga> comandos) {
        try {
            return json.writeValueAsString(comandos.stream()
                    .map(c -> new ComandoJson(c.tipo(), c.destino().name(), c.payload()))
                    .toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar comandos pendentes da saga", e);
        }
    }

    private List<ComandoSaga> ler(String texto) {
        try {
            return json.readValue(texto, LISTA_DE_COMANDOS).stream()
                    .map(c -> new ComandoSaga(c.tipo(), ComandoSaga.Destino.valueOf(c.destino()), c.payload()))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Comandos pendentes da saga ilegíveis: " + texto, e);
        }
    }
}
