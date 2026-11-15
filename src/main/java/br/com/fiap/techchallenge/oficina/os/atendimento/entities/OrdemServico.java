package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento.AprovadoPor;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Agregado raiz <b>Ordem de Serviço</b> — o que o cliente acompanha.
 *
 * <p>Na fase 4 a OS deixou de ter itens editáveis, reservas de peça e o orçamento
 * completo: eles vivem na Execução e no Billing (ADR 006). O que fica aqui é o ciclo
 * de vida, cópias somente leitura do que os outros serviços decidiram (itens do
 * diagnóstico, resumo do orçamento, link de pagamento) e o histórico de transições.
 *
 * <p>Quem chama estes métodos é a saga ({@code SagaOrdemServico}), a partir dos eventos
 * dos participantes. As transições são estritas: chamar no status errado lança
 * {@link TransicaoStatusInvalidaException}. Tolerar mensagem fora de ordem é decisão
 * da saga, não do agregado.
 */
public class OrdemServico {

    private static final Set<StatusOS> PODE_CONCLUIR_DIAGNOSTICO =
            EnumSet.of(StatusOS.RECEBIDA, StatusOS.EM_DIAGNOSTICO);

    private final OrdemServicoId id;
    private final ClienteId clienteId;
    private final VeiculoId veiculoId;
    private final String relatoCliente;
    private StatusOS status;
    private List<ItemOS> itens;
    private ResumoOrcamento orcamento;
    private Pagamento pagamento;
    private MotivoCancelamento motivoCancelamento;

    private final OffsetDateTime criadaEm;
    private OffsetDateTime diagnosticoIniciadoEm;
    private OffsetDateTime execucaoIniciadaEm;
    private OffsetDateTime finalizadaEm;
    private OffsetDateTime entregueEm;
    private OffsetDateTime canceladaEm;

    /** Transições ainda não persistidas — o gateway grava no histórico e limpa. */
    private final List<TransicaoStatus> novasTransicoes = new ArrayList<>();

    private OrdemServico(Dados d) {
        this.id = Objects.requireNonNull(d.id, "id");
        this.clienteId = Objects.requireNonNull(d.clienteId, "clienteId");
        this.veiculoId = Objects.requireNonNull(d.veiculoId, "veiculoId");
        this.relatoCliente = d.relatoCliente;
        this.status = Objects.requireNonNull(d.status, "status");
        this.itens = List.copyOf(d.itens == null ? List.of() : d.itens);
        this.orcamento = d.orcamento;
        this.pagamento = d.pagamento == null ? Pagamento.NENHUM : d.pagamento;
        this.motivoCancelamento = d.motivoCancelamento;
        this.criadaEm = Objects.requireNonNull(d.criadaEm, "criadaEm");
        this.diagnosticoIniciadoEm = d.diagnosticoIniciadoEm;
        this.execucaoIniciadaEm = d.execucaoIniciadaEm;
        this.finalizadaEm = d.finalizadaEm;
        this.entregueEm = d.entregueEm;
        this.canceladaEm = d.canceladaEm;
    }

    public static OrdemServico abrir(ClienteId clienteId, VeiculoId veiculoId, String relatoCliente,
                                     OffsetDateTime agora) {
        Dados d = new Dados();
        d.id = OrdemServicoId.novo();
        d.clienteId = clienteId;
        d.veiculoId = veiculoId;
        d.relatoCliente = relatoCliente == null || relatoCliente.isBlank() ? null : relatoCliente.trim();
        d.status = StatusOS.RECEBIDA;
        d.criadaEm = agora;
        OrdemServico os = new OrdemServico(d);
        os.novasTransicoes.add(new TransicaoStatus(null, StatusOS.RECEBIDA, agora, "OS aberta"));
        return os;
    }

    /** Reconstrução a partir da persistência — sem validar transições nem gerar histórico. */
    public static OrdemServico reconstituir(Dados dados) {
        return new OrdemServico(dados);
    }

    // ---------- Transições (chamadas pela saga) ----------

    public void iniciarDiagnostico(OffsetDateTime em) {
        exigirStatus(StatusOS.RECEBIDA, StatusOS.EM_DIAGNOSTICO);
        this.diagnosticoIniciadoEm = Objects.requireNonNull(em, "em");
        mudarPara(StatusOS.EM_DIAGNOSTICO, em, "Diagnóstico iniciado");
    }

    /**
     * Guarda a cópia dos itens diagnosticados. Aceita a OS ainda em RECEBIDA: o evento
     * de início do diagnóstico pode chegar depois do de conclusão (fila não garante
     * ordem), e por isso o momento de início vem junto, neste evento.
     */
    public void concluirDiagnostico(List<ItemOS> itensDiagnosticados, OffsetDateTime iniciadoEm,
                                    OffsetDateTime concluidoEm) {
        if (!PODE_CONCLUIR_DIAGNOSTICO.contains(status)) {
            throw new TransicaoStatusInvalidaException(
                    "Diagnóstico só pode ser concluído em RECEBIDA ou EM_DIAGNOSTICO; status atual: " + status);
        }
        if (status == StatusOS.RECEBIDA) {
            this.diagnosticoIniciadoEm = iniciadoEm == null ? concluidoEm : iniciadoEm;
            mudarPara(StatusOS.EM_DIAGNOSTICO, this.diagnosticoIniciadoEm, "Diagnóstico iniciado");
        }
        this.itens = List.copyOf(Objects.requireNonNull(itensDiagnosticados, "itens"));
    }

    public void registrarOrcamento(ResumoOrcamento orcamentoGerado) {
        exigirStatus(StatusOS.EM_DIAGNOSTICO, StatusOS.AGUARDANDO_APROVACAO);
        this.orcamento = Objects.requireNonNull(orcamentoGerado, "orcamento");
        mudarPara(StatusOS.AGUARDANDO_APROVACAO, orcamentoGerado.geradoEm(), "Orçamento gerado");
    }

    public void aprovarOrcamento(AprovadoPor por, OffsetDateTime em) {
        exigirStatus(StatusOS.AGUARDANDO_APROVACAO, StatusOS.AGUARDANDO_PAGAMENTO);
        this.orcamento = orcamento.aprovado(por, em);
        mudarPara(StatusOS.AGUARDANDO_PAGAMENTO, em,
                por == AprovadoPor.CLIENTE ? "Orçamento aprovado pelo cliente" : "Orçamento aprovado pelo atendente");
    }

    /** Link de cobrança criado pelo Billing. Não muda o status — só o que o cliente vê. */
    public void registrarLinkPagamento(String link, OffsetDateTime expiraEm) {
        if (status != StatusOS.AGUARDANDO_PAGAMENTO) {
            throw new TransicaoStatusInvalidaException(
                    "Link de pagamento só cabe em AGUARDANDO_PAGAMENTO; status atual: " + status);
        }
        this.pagamento = pagamento.comLink(link, expiraEm);
    }

    public void confirmarPagamento(OffsetDateTime em) {
        exigirStatus(StatusOS.AGUARDANDO_PAGAMENTO, StatusOS.EM_EXECUCAO);
        this.pagamento = pagamento.confirmado(em);
        this.execucaoIniciadaEm = em;
        mudarPara(StatusOS.EM_EXECUCAO, em, "Pagamento confirmado");
    }

    public void finalizar(OffsetDateTime em) {
        exigirStatus(StatusOS.EM_EXECUCAO, StatusOS.FINALIZADA);
        this.finalizadaEm = em;
        mudarPara(StatusOS.FINALIZADA, em, "Reparo finalizado");
    }

    public void entregar(OffsetDateTime em) {
        exigirStatus(StatusOS.FINALIZADA, StatusOS.ENTREGUE);
        this.entregueEm = em;
        mudarPara(StatusOS.ENTREGUE, em, "Veículo entregue");
    }

    /**
     * Cancelamento por compensação. A OS vai para CANCELADA quando a compensação
     * <b>começa</b> — para o cliente a decisão já foi tomada (saga.md §3).
     */
    public void cancelar(MotivoCancelamento motivo, OffsetDateTime em) {
        Objects.requireNonNull(motivo, "motivo");
        if (!status.cancelavel()) {
            throw new TransicaoStatusInvalidaException(status, StatusOS.CANCELADA);
        }
        this.motivoCancelamento = motivo;
        this.canceladaEm = em;
        mudarPara(StatusOS.CANCELADA, em, motivo.descricao());
    }

    public boolean pertenceA(ClienteId cliente) {
        return clienteId.equals(cliente);
    }

    private void exigirStatus(StatusOS esperado, StatusOS desejado) {
        if (this.status != esperado) {
            throw new TransicaoStatusInvalidaException(this.status, desejado);
        }
    }

    private void mudarPara(StatusOS novo, OffsetDateTime em, String motivo) {
        novasTransicoes.add(new TransicaoStatus(this.status, novo, em, motivo));
        this.status = novo;
    }

    // ---------- Histórico ----------

    /** Transições desde a última persistência, na ordem em que aconteceram. */
    public List<TransicaoStatus> novasTransicoes() {
        return Collections.unmodifiableList(novasTransicoes);
    }

    public void transicoesPersistidas() {
        novasTransicoes.clear();
    }

    // ---------- Acessores ----------

    public OrdemServicoId id()                    { return id; }
    public ClienteId clienteId()                  { return clienteId; }
    public VeiculoId veiculoId()                  { return veiculoId; }
    public String relatoCliente()                 { return relatoCliente; }
    public StatusOS status()                      { return status; }
    public List<ItemOS> itens()                   { return itens; }
    public ResumoOrcamento orcamento()            { return orcamento; }
    public Pagamento pagamento()                  { return pagamento; }
    public MotivoCancelamento motivoCancelamento() { return motivoCancelamento; }
    public OffsetDateTime criadaEm()              { return criadaEm; }
    public OffsetDateTime diagnosticoIniciadoEm() { return diagnosticoIniciadoEm; }
    public OffsetDateTime execucaoIniciadaEm()    { return execucaoIniciadaEm; }
    public OffsetDateTime finalizadaEm()          { return finalizadaEm; }
    public OffsetDateTime entregueEm()            { return entregueEm; }
    public OffsetDateTime canceladaEm()           { return canceladaEm; }

    /** Soma dos itens diagnosticados — o Billing calcula o oficial, isto é só exibição. */
    public Dinheiro totalDosItens() {
        Dinheiro total = Dinheiro.ZERO;
        for (ItemOS item : itens) {
            total = total.somar(item.subtotal());
        }
        return total;
    }

    /** Momento da última mudança de estado — usado na consulta de status. */
    public OffsetDateTime atualizadaEm() {
        OffsetDateTime ultima = criadaEm;
        OffsetDateTime[] marcos = {
                diagnosticoIniciadoEm,
                orcamento == null ? null : orcamento.geradoEm(),
                orcamento == null ? null : orcamento.aprovadoEm(),
                execucaoIniciadaEm, finalizadaEm, entregueEm, canceladaEm};
        for (OffsetDateTime marco : marcos) {
            if (marco != null && marco.isAfter(ultima)) {
                ultima = marco;
            }
        }
        return ultima;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrdemServico that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Parâmetros de reconstituição — evita um construtor de quinze argumentos posicionais. */
    public static final class Dados {
        public OrdemServicoId id;
        public ClienteId clienteId;
        public VeiculoId veiculoId;
        public String relatoCliente;
        public StatusOS status;
        public List<ItemOS> itens;
        public ResumoOrcamento orcamento;
        public Pagamento pagamento;
        public MotivoCancelamento motivoCancelamento;
        public OffsetDateTime criadaEm;
        public OffsetDateTime diagnosticoIniciadoEm;
        public OffsetDateTime execucaoIniciadaEm;
        public OffsetDateTime finalizadaEm;
        public OffsetDateTime entregueEm;
        public OffsetDateTime canceladaEm;
    }
}
