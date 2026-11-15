package br.com.fiap.techchallenge.oficina.os.atendimento.controllers;

import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.AberturaOrdemServicoResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.AbrirOrdemServicoRequest;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.HistoricoOSResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.OrdemServicoResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.StatusOSResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.AcessoNegadoAOrdemServicoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.MetricasGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.presenters.OrdemServicoPresenter;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.AbrirOrdemServicoUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.BuscarOrdemServicoPorIdUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.ConsultarHistoricoOSUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.EntregarVeiculoUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.ListarOrdensServicoUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.NotificarStatusOSUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.controllers.PosCommitSaga;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.DecidirOrcamentoUseCase;
import br.com.fiap.techchallenge.oficina.os.saga.usecases.EfeitoSaga;
import br.com.fiap.techchallenge.oficina.os.shared.usecases.ExecutorTransacional;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Interface Adapter das ordens de serviço: traduz requisições da API para use cases,
 * define a fronteira de transação e aplica, depois do commit, notificação e métricas.
 */
public class OrdemServicoController {

    private final AbrirOrdemServicoUseCase abrir;
    private final BuscarOrdemServicoPorIdUseCase buscar;
    private final ListarOrdensServicoUseCase listar;
    private final ConsultarHistoricoOSUseCase historico;
    private final DecidirOrcamentoUseCase decidir;
    private final EntregarVeiculoUseCase entregar;
    private final NotificarStatusOSUseCase notificar;
    private final PosCommitSaga posCommit;
    private final ExecutorTransacional tx;
    private final MetricasGateway metricas;
    private final OrdemServicoPresenter presenter = new OrdemServicoPresenter();

    public OrdemServicoController(AbrirOrdemServicoUseCase abrir, OrdemServicoGateway ordens,
                                  DecidirOrcamentoUseCase decidir, EntregarVeiculoUseCase entregar,
                                  NotificarStatusOSUseCase notificar, PosCommitSaga posCommit,
                                  ExecutorTransacional tx, MetricasGateway metricas) {
        this.abrir = abrir;
        this.buscar = new BuscarOrdemServicoPorIdUseCase(ordens);
        this.listar = new ListarOrdensServicoUseCase(ordens);
        this.historico = new ConsultarHistoricoOSUseCase(ordens);
        this.decidir = decidir;
        this.entregar = entregar;
        this.notificar = notificar;
        this.posCommit = posCommit;
        this.tx = tx;
        this.metricas = metricas;
    }

    // ---------- Atendimento (funcionários) ----------

    public AberturaOrdemServicoResponse abrir(AbrirOrdemServicoRequest req) {
        OrdemServico os = medindoFalha("abrir", () -> tx.emTransacao(() -> abrir.executar(
                new AbrirOrdemServicoUseCase.Input(
                        new AbrirOrdemServicoUseCase.DadosCliente(req.cliente().documento(), req.cliente().nome(),
                                req.cliente().email(), req.cliente().telefone()),
                        new AbrirOrdemServicoUseCase.DadosVeiculo(req.veiculo().placa(), req.veiculo().marca(),
                                req.veiculo().modelo(), req.veiculo().ano()),
                        req.relatoCliente()))));
        metricas.ordemServicoAberta();
        posCommit.notificarComSeguranca(os);
        return presenter.apresentarAbertura(os);
    }

    public List<OrdemServicoResponse> listar(String status, UUID clienteId, UUID veiculoId) {
        OrdemServicoGateway.Filtro filtro = new OrdemServicoGateway.Filtro(
                status == null ? null : StatusOS.valueOf(status),
                clienteId == null ? null : ClienteId.de(clienteId),
                veiculoId == null ? null : VeiculoId.de(veiculoId));
        return presenter.apresentar(tx.emTransacao(() -> listar.executar(filtro)));
    }

    public OrdemServicoResponse buscar(UUID id) {
        return presenter.apresentar(tx.emTransacao(() -> buscar.executar(OrdemServicoId.de(id))));
    }

    public StatusOSResponse consultarStatus(UUID id) {
        return presenter.apresentarStatus(tx.emTransacao(() -> buscar.executar(OrdemServicoId.de(id))));
    }

    public HistoricoOSResponse historico(UUID id) {
        OrdemServicoId osId = OrdemServicoId.de(id);
        return tx.emTransacao(() -> presenter.apresentarHistorico(buscar.executar(osId), historico.executar(osId)));
    }

    /** Atendente registra a decisão em nome do cliente (balcão, telefone). */
    public OrdemServicoResponse decidirOrcamento(UUID id, boolean aprovado) {
        return presenter.apresentar(decidirEAplicar(OrdemServicoId.de(id), aprovado, null));
    }

    public OrdemServicoResponse entregar(UUID id) {
        OrdemServico os = medindoFalha("entregar",
                () -> tx.emTransacao(() -> entregar.executar(OrdemServicoId.de(id))));
        posCommit.notificarComSeguranca(os);
        posCommit.registrarFaseConcluida(os);
        return presenter.apresentar(os);
    }

    // ---------- Área do cliente (JWT por CPF) ----------

    public List<OrdemServicoResponse> listarDoCliente(UUID clienteId) {
        return listar(null, clienteId, null);
    }

    public OrdemServicoResponse consultarDoCliente(UUID osId, UUID clienteId) {
        return presenter.apresentar(tx.emTransacao(() -> buscarDoCliente(osId, clienteId)));
    }

    public StatusOSResponse consultarStatusDoCliente(UUID osId, UUID clienteId) {
        return presenter.apresentarStatus(tx.emTransacao(() -> buscarDoCliente(osId, clienteId)));
    }

    public StatusOSResponse decidirOrcamentoDoCliente(UUID osId, UUID clienteId, boolean aprovado) {
        return presenter.apresentarStatus(decidirEAplicar(OrdemServicoId.de(osId), aprovado, ClienteId.de(clienteId)));
    }

    private OrdemServico buscarDoCliente(UUID osId, UUID clienteId) {
        OrdemServico os = buscar.executar(OrdemServicoId.de(osId));
        if (!os.pertenceA(ClienteId.de(clienteId))) {
            throw new AcessoNegadoAOrdemServicoException(OrdemServicoId.de(osId));
        }
        return os;
    }

    // ---------- Mecânica ----------

    private OrdemServico decidirEAplicar(OrdemServicoId osId, boolean aprovado, ClienteId cliente) {
        String operacao = aprovado ? "aprovarOrcamento" : "recusarOrcamento";
        EfeitoSaga efeito = medindoFalha(operacao, () -> tx.emTransacao(
                () -> decidir.executar(new DecidirOrcamentoUseCase.Input(osId, aprovado, cliente))));
        posCommit.aplicar(efeito, false);
        return efeito.os();
    }

    private <T> T medindoFalha(String operacao, Supplier<T> acao) {
        try {
            return acao.get();
        } catch (RuntimeException e) {
            metricas.falhaProcessamento(operacao);
            throw e;
        }
    }
}
