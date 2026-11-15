package br.com.fiap.techchallenge.oficina.os.suporte;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.TransicaoStatus;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Veiculo;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.VeiculoGateway;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ComandoSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.InboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.OutboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Documento;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Placa;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gateways em memória para testar use cases sem banco nem fila. Implementam só o
 * comportamento que os use cases observam — persistência real é coberta pelos ITs.
 */
public final class Fakes {

    private Fakes() {}

    public static final class Ordens implements OrdemServicoGateway {
        public final Map<OrdemServicoId, OrdemServico> dados = new LinkedHashMap<>();
        public final List<TransicaoStatus> historico = new ArrayList<>();

        @Override
        public OrdemServico salvar(OrdemServico os) {
            historico.addAll(os.novasTransicoes());
            os.transicoesPersistidas();
            dados.put(os.id(), os);
            return os;
        }

        @Override
        public Optional<OrdemServico> buscarPorId(OrdemServicoId id) {
            return Optional.ofNullable(dados.get(id));
        }

        @Override
        public List<OrdemServico> listar(Filtro filtro) {
            return dados.values().stream()
                    .filter(os -> filtro.status() == null || os.status() == filtro.status())
                    .filter(os -> filtro.clienteId() == null || os.clienteId().equals(filtro.clienteId()))
                    .filter(os -> filtro.veiculoId() == null || os.veiculoId().equals(filtro.veiculoId()))
                    .toList();
        }

        @Override
        public List<TransicaoStatus> historico(OrdemServicoId id) {
            return List.copyOf(historico);
        }
    }

    public static final class Clientes implements ClienteGateway {
        public final Map<ClienteId, Cliente> dados = new LinkedHashMap<>();

        public Clientes com(Cliente... clientes) {
            for (Cliente c : clientes) {
                dados.put(c.id(), c);
            }
            return this;
        }

        @Override public Cliente salvar(Cliente c) { dados.put(c.id(), c); return c; }
        @Override public Optional<Cliente> buscarPorId(ClienteId id) { return Optional.ofNullable(dados.get(id)); }
        @Override public Optional<Cliente> buscarPorDocumento(Documento d) {
            return dados.values().stream().filter(c -> c.documento().equals(d)).findFirst();
        }
        @Override public List<Cliente> listar() { return List.copyOf(dados.values()); }
        @Override public void remover(ClienteId id) { dados.remove(id); }
    }

    public static final class Veiculos implements VeiculoGateway {
        public final Map<VeiculoId, Veiculo> dados = new LinkedHashMap<>();

        public Veiculos com(Veiculo... veiculos) {
            for (Veiculo v : veiculos) {
                dados.put(v.id(), v);
            }
            return this;
        }

        @Override public Veiculo salvar(Veiculo v) { dados.put(v.id(), v); return v; }
        @Override public Optional<Veiculo> buscarPorId(VeiculoId id) { return Optional.ofNullable(dados.get(id)); }
        @Override public Optional<Veiculo> buscarPorPlaca(Placa p) {
            return dados.values().stream().filter(v -> v.placa().equals(p)).findFirst();
        }
        @Override public List<Veiculo> listarPorCliente(ClienteId id) {
            return dados.values().stream().filter(v -> v.clienteId().equals(id)).toList();
        }
        @Override public List<Veiculo> listar() { return List.copyOf(dados.values()); }
        @Override public void remover(VeiculoId id) { dados.remove(id); }
    }

    public static final class Sagas implements SagaGateway {
        public final Map<OrdemServicoId, SagaOrdemServico> dados = new LinkedHashMap<>();

        @Override public void salvar(SagaOrdemServico saga) { dados.put(saga.osId(), saga); }
        @Override public Optional<SagaOrdemServico> buscarPorOs(OrdemServicoId id) { return Optional.ofNullable(dados.get(id)); }
        @Override public Optional<SagaOrdemServico> buscarPorOsComLock(OrdemServicoId id) { return buscarPorOs(id); }
        @Override public List<OrdemServicoId> listarComPrazoVencido(OffsetDateTime agora, int limite) {
            return dados.values().stream()
                    .filter(s -> s.prazo() != null && !s.prazo().isAfter(agora))
                    .sorted(Comparator.comparing(SagaOrdemServico::prazo))
                    .limit(limite)
                    .map(SagaOrdemServico::osId)
                    .toList();
        }
    }

    /** Guarda cada comando enfileirado com sua correlação e causa. */
    public static final class Outbox implements OutboxGateway {
        public record Enfileirado(ComandoSaga comando, OrdemServicoId correlationId, UUID causationId) {}

        public final List<Enfileirado> enfileirados = new ArrayList<>();

        @Override
        public void enfileirar(List<ComandoSaga> comandos, OrdemServicoId correlationId, UUID causationId) {
            comandos.forEach(c -> enfileirados.add(new Enfileirado(c, correlationId, causationId)));
        }

        public List<String> tipos() {
            return enfileirados.stream().map(e -> e.comando().tipo()).toList();
        }
    }

    public static final class Inbox implements InboxGateway {
        public final Set<UUID> processadas = new HashSet<>();

        @Override
        public boolean registrarSeNova(UUID messageId, String tipo, OrdemServicoId correlationId) {
            return processadas.add(messageId);
        }
    }
}
