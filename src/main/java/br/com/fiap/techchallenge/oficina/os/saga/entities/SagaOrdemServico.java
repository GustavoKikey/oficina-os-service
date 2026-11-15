package br.com.fiap.techchallenge.oficina.os.saga.entities;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ItemOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.MotivoCancelamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento.AprovadoPor;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Veiculo;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ComandoSaga.Destino;
import br.com.fiap.techchallenge.oficina.os.saga.entities.ComandoSaga.TiposComando;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.DiagnosticoConcluido;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.DiagnosticoIniciado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.EstornoFalhou;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrcamentoCancelado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrcamentoGerado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrcamentoNaoGerado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrdemExecucaoAberta;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.OrdemExecucaoCancelada;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoConfirmado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoEstornado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoExpirado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoRecusado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.PagamentoSolicitado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.ReparoFinalizado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.ReparoIniciado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.ReparoLiberado;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga.ReparoNaoLiberado;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Saga orquestrada da Ordem de Serviço ({@code docs/fase-4/saga.md}, ADR 007).
 *
 * <p>Máquina de estados <b>pura</b>: não importa framework, não acessa banco nem fila.
 * Cada método recebe a OS, aplica a transição na saga e na OS, e devolve os comandos
 * que precisam ser enviados. Quem persiste os dois e grava os comandos no outbox, numa
 * única transação, é o use case.
 *
 * <p>Regras que valem para todo evento recebido:
 * <ul>
 *   <li>evento que não cabe no estado atual é <b>ignorado</b> (resultado
 *       {@code aplicado=false}) — é mensagem duplicada, atrasada ou de passo superado;</li>
 *   <li>eventos carregam os dados necessários, então a ordem de chegada pode variar
 *       (ex.: {@code DiagnosticoConcluido} antes de {@code DiagnosticoIniciado});</li>
 *   <li>falha de negócio de um participante dispara as compensações dos passos já
 *       concluídos, todas de uma vez, e a OS é cancelada.</li>
 * </ul>
 */
public class SagaOrdemServico {

    private final OrdemServicoId osId;
    private EstadoSaga estado;
    private EstadoSaga estadoAntesDaFalha;
    private OffsetDateTime prazo;
    private int tentativas;
    private List<ComandoSaga> comandosPendentes;
    private String motivoFalha;
    private final OffsetDateTime iniciadaEm;
    private OffsetDateTime atualizadaEm;
    private final long versao;

    private SagaOrdemServico(OrdemServicoId osId, EstadoSaga estado, EstadoSaga estadoAntesDaFalha,
                             OffsetDateTime prazo, int tentativas, List<ComandoSaga> comandosPendentes,
                             String motivoFalha, OffsetDateTime iniciadaEm, OffsetDateTime atualizadaEm,
                             long versao) {
        this.osId = Objects.requireNonNull(osId, "osId");
        this.estado = Objects.requireNonNull(estado, "estado");
        this.estadoAntesDaFalha = estadoAntesDaFalha;
        this.prazo = prazo;
        this.tentativas = tentativas;
        this.comandosPendentes = new ArrayList<>(comandosPendentes);
        this.motivoFalha = motivoFalha;
        this.iniciadaEm = Objects.requireNonNull(iniciadaEm, "iniciadaEm");
        this.atualizadaEm = Objects.requireNonNull(atualizadaEm, "atualizadaEm");
        this.versao = versao;
    }

    /** Abertura da OS: a saga nasce pedindo à Execução que coloque a OS na fila. */
    public static Inicio iniciar(OrdemServico os, Veiculo veiculo, PrazosSaga prazos, OffsetDateTime agora) {
        if (!os.veiculoId().equals(veiculo.id())) {
            throw new IllegalArgumentException("Veículo " + veiculo.id() + " não é o da OS " + os.id());
        }
        Map<String, Object> dadosVeiculo = mapa(
                "placa", veiculo.placa().valor(),
                "marca", veiculo.marca(),
                "modelo", veiculo.modelo(),
                "ano", veiculo.ano());
        ComandoSaga abrir = comando(TiposComando.ABRIR_ORDEM_EXECUCAO, Destino.EXECUCAO, os,
                "veiculo", dadosVeiculo,
                "relatoCliente", os.relatoCliente(),
                "abertaEm", os.criadaEm().toString());
        SagaOrdemServico saga = new SagaOrdemServico(os.id(), EstadoSaga.EXECUCAO_SOLICITADA, null,
                agora.plus(prazos.resposta()), 0, List.of(abrir), null, agora, agora, 0);
        return new Inicio(saga, List.of(abrir));
    }

    public static SagaOrdemServico reconstituir(OrdemServicoId osId, EstadoSaga estado,
                                                EstadoSaga estadoAntesDaFalha, OffsetDateTime prazo,
                                                int tentativas, List<ComandoSaga> comandosPendentes,
                                                String motivoFalha, OffsetDateTime iniciadaEm,
                                                OffsetDateTime atualizadaEm, long versao) {
        return new SagaOrdemServico(osId, estado, estadoAntesDaFalha, prazo, tentativas,
                comandosPendentes, motivoFalha, iniciadaEm, atualizadaEm, versao);
    }

    // =====================================================================
    // Eventos dos participantes
    // =====================================================================

    /**
     * Aplica um evento de Execução ou Billing.
     *
     * @param cliente dono da OS — o comando {@code GerarOrcamento} leva nome e e-mail
     */
    public ResultadoSaga aoReceber(EventoSaga evento, OrdemServico os, Cliente cliente,
                                   PrazosSaga prazos, OffsetDateTime agora) {
        exigirMesmaOs(os);
        ResultadoSaga resultado = switch (evento) {
            case OrdemExecucaoAberta e -> aoAbrirOrdemExecucao();
            case DiagnosticoIniciado e -> aoIniciarDiagnostico(e, os);
            case DiagnosticoConcluido e -> aoConcluirDiagnostico(e, os, cliente, prazos, agora);
            case OrcamentoGerado e -> aoGerarOrcamento(e, os, prazos, agora);
            case OrcamentoNaoGerado e -> quandoEm(EstadoSaga.ORCAMENTO_SOLICITADO,
                    () -> compensar(os, MotivoCancelamento.ORCAMENTO_NAO_GERADO, prazos, agora,
                            cancelarOrdemExecucao(os, MotivoCancelamento.ORCAMENTO_NAO_GERADO)));
            case PagamentoSolicitado e -> aoSolicitarPagamento(e, os);
            case PagamentoConfirmado e -> aoConfirmarPagamento(e, os, prazos, agora);
            case PagamentoRecusado e -> quandoEm(EstadoSaga.PAGAMENTO_SOLICITADO,
                    () -> compensarAntesDeCobrar(os, MotivoCancelamento.PAGAMENTO_RECUSADO, prazos, agora));
            case PagamentoExpirado e -> quandoEm(EstadoSaga.PAGAMENTO_SOLICITADO,
                    () -> compensarAntesDeCobrar(os, MotivoCancelamento.PAGAMENTO_EXPIRADO, prazos, agora));
            case ReparoLiberado e -> quandoEm(EstadoSaga.REPARO_SOLICITADO, this::aoLiberarReparo);
            case ReparoIniciado e -> estado == EstadoSaga.REPARO_SOLICITADO
                    ? aoLiberarReparo()   // ReparoLiberado se perdeu: começar o reparo prova a liberação
                    : ResultadoSaga.ignorado("informativo");
            case ReparoNaoLiberado e -> quandoEm(EstadoSaga.REPARO_SOLICITADO,
                    () -> compensar(os, MotivoCancelamento.REPARO_NAO_LIBERADO, prazos, agora,
                            estornarPagamento(os, MotivoCancelamento.REPARO_NAO_LIBERADO),
                            cancelarOrdemExecucao(os, MotivoCancelamento.REPARO_NAO_LIBERADO)));
            case ReparoFinalizado e -> aoFinalizarReparo(e, os);
            case OrdemExecucaoCancelada e -> confirmarCompensacao(evento.tipo());
            case OrcamentoCancelado e -> confirmarCompensacao(evento.tipo());
            case PagamentoEstornado e -> confirmarCompensacao(evento.tipo());
            case EstornoFalhou e -> quandoEm(EstadoSaga.COMPENSANDO,
                    () -> falhar("Estorno falhou no Mercado Pago: " + e.erro()));
        };
        if (resultado.aplicado()) {
            atualizadaEm = agora;
        }
        return resultado;
    }

    private ResultadoSaga aoAbrirOrdemExecucao() {
        return quandoEm(EstadoSaga.EXECUCAO_SOLICITADA, () -> {
            aguardarPessoa(EstadoSaga.EM_DIAGNOSTICO);
            return ResultadoSaga.aplicado("OS na fila de diagnóstico");
        });
    }

    private ResultadoSaga aoIniciarDiagnostico(DiagnosticoIniciado e, OrdemServico os) {
        if (estado != EstadoSaga.EXECUCAO_SOLICITADA && estado != EstadoSaga.EM_DIAGNOSTICO) {
            return ResultadoSaga.ignorado("diagnóstico já superado no estado " + estado);
        }
        if (estado == EstadoSaga.EXECUCAO_SOLICITADA) {
            aguardarPessoa(EstadoSaga.EM_DIAGNOSTICO);   // OrdemExecucaoAberta se perdeu
        }
        if (os.status() == StatusOS.RECEBIDA) {
            os.iniciarDiagnostico(e.iniciadoEm());
        }
        return ResultadoSaga.aplicado("diagnóstico iniciado");
    }

    private ResultadoSaga aoConcluirDiagnostico(DiagnosticoConcluido e, OrdemServico os, Cliente cliente,
                                               PrazosSaga prazos, OffsetDateTime agora) {
        if (estado != EstadoSaga.EXECUCAO_SOLICITADA && estado != EstadoSaga.EM_DIAGNOSTICO) {
            return ResultadoSaga.ignorado("diagnóstico já superado no estado " + estado);
        }
        if (!os.clienteId().equals(cliente.id())) {
            throw new IllegalArgumentException("Cliente " + cliente.id() + " não é o dono da OS " + os.id());
        }
        os.concluirDiagnostico(e.itens(), e.iniciadoEm(), e.concluidoEm());
        ComandoSaga gerar = comando(TiposComando.GERAR_ORCAMENTO, Destino.BILLING, os,
                "cliente", mapa("id", cliente.id().valor().toString(),
                                "nome", cliente.nome(),
                                "email", cliente.email()),
                "itens", e.itens().stream().map(SagaOrdemServico::paraMapa).toList());
        return solicitar(EstadoSaga.ORCAMENTO_SOLICITADO, gerar, prazos.resposta(), agora,
                "orçamento solicitado ao Billing");
    }

    private ResultadoSaga aoGerarOrcamento(OrcamentoGerado e, OrdemServico os, PrazosSaga prazos,
                                          OffsetDateTime agora) {
        return quandoEm(EstadoSaga.ORCAMENTO_SOLICITADO, () -> {
            os.registrarOrcamento(ResumoOrcamento.gerado(e.orcamentoId(), e.valorTotal(), e.geradoEm()));
            aguardarPessoa(EstadoSaga.AGUARDANDO_APROVACAO);
            prazo = agora.plus(prazos.aprovacao());
            return ResultadoSaga.aplicado("orçamento aguardando aprovação do cliente");
        });
    }

    private ResultadoSaga aoSolicitarPagamento(PagamentoSolicitado e, OrdemServico os) {
        return quandoEm(EstadoSaga.PAGAMENTO_SOLICITADO, () -> {
            os.registrarLinkPagamento(e.linkPagamento(), e.expiraEm());
            // O Billing respondeu: daqui em diante espera-se o cliente pagar, não um
            // serviço. O prazo de pagamento (já definido na aprovação) continua valendo.
            comandosPendentes.clear();
            return ResultadoSaga.aplicado("link de pagamento disponível ao cliente");
        });
    }

    private ResultadoSaga aoConfirmarPagamento(PagamentoConfirmado e, OrdemServico os, PrazosSaga prazos,
                                              OffsetDateTime agora) {
        return quandoEm(EstadoSaga.PAGAMENTO_SOLICITADO, () -> {
            os.confirmarPagamento(e.confirmadoEm());
            ComandoSaga liberar = comando(TiposComando.LIBERAR_PARA_REPARO, Destino.EXECUCAO, os,
                    "orcamentoId", os.orcamento().orcamentoId().toString());
            return solicitar(EstadoSaga.REPARO_SOLICITADO, liberar, prazos.resposta(), agora,
                    "pagamento confirmado — reparo solicitado à Execução");
        });
    }

    private ResultadoSaga aoLiberarReparo() {
        aguardarPessoa(EstadoSaga.EM_REPARO);
        return ResultadoSaga.aplicado("OS na fila de reparo");
    }

    private ResultadoSaga aoFinalizarReparo(ReparoFinalizado e, OrdemServico os) {
        if (estado != EstadoSaga.EM_REPARO && estado != EstadoSaga.REPARO_SOLICITADO) {
            return ResultadoSaga.ignorado("reparo não esperado no estado " + estado);
        }
        os.finalizar(e.finalizadoEm());
        aguardarPessoa(EstadoSaga.CONCLUIDA);
        return ResultadoSaga.aplicado("saga concluída");
    }

    private ResultadoSaga confirmarCompensacao(String evento) {
        boolean compensandoOuFalhaNaCompensacao = estado == EstadoSaga.COMPENSANDO
                || (estado == EstadoSaga.FALHA && estadoAntesDaFalha == EstadoSaga.COMPENSANDO);
        if (!compensandoOuFalhaNaCompensacao) {
            return ResultadoSaga.ignorado("nenhuma compensação em andamento");
        }
        boolean removido = comandosPendentes.removeIf(c -> evento.equals(c.confirmacaoEsperada()));
        if (!removido) {
            return ResultadoSaga.ignorado("compensação já confirmada");
        }
        if (comandosPendentes.isEmpty()) {
            // Confirmação atrasada também tira a saga de FALHA: a limpeza terminou.
            estado = EstadoSaga.COMPENSADA;
            estadoAntesDaFalha = null;
            motivoFalha = null;
            prazo = null;
            return ResultadoSaga.aplicado("todas as compensações confirmadas");
        }
        return ResultadoSaga.aplicado(evento + " confirmado; aguardando " + comandosPendentes.size() + " compensação(ões)");
    }

    // =====================================================================
    // Decisões de pessoas (API)
    // =====================================================================

    public ResultadoSaga aprovarOrcamento(OrdemServico os, AprovadoPor por, PrazosSaga prazos,
                                          OffsetDateTime agora) {
        exigirMesmaOs(os);
        exigirAguardandoAprovacao();
        os.aprovarOrcamento(por, agora);
        ComandoSaga solicitar = comando(TiposComando.SOLICITAR_PAGAMENTO, Destino.BILLING, os,
                "orcamentoId", os.orcamento().orcamentoId().toString(),
                "aprovadoPor", por.name(),
                "aprovadoEm", agora.toString());
        ResultadoSaga resultado = solicitar(EstadoSaga.PAGAMENTO_SOLICITADO, solicitar, prazos.pagamento(), agora,
                "orçamento aprovado — cobrança solicitada ao Billing");
        atualizadaEm = agora;
        return resultado;
    }

    public ResultadoSaga recusarOrcamento(OrdemServico os, PrazosSaga prazos, OffsetDateTime agora) {
        exigirMesmaOs(os);
        exigirAguardandoAprovacao();
        ResultadoSaga resultado = compensarAntesDeCobrar(os, MotivoCancelamento.ORCAMENTO_RECUSADO, prazos, agora);
        atualizadaEm = agora;
        return resultado;
    }

    private void exigirAguardandoAprovacao() {
        if (estado != EstadoSaga.AGUARDANDO_APROVACAO) {
            throw new TransicaoSagaInvalidaException(
                    "O orçamento da OS " + osId.valor() + " não está aguardando aprovação (saga em " + estado + ")");
        }
    }

    // =====================================================================
    // Prazos
    // =====================================================================

    /**
     * Chamado pelo verificador agendado. Prazo de pessoa vencido vira recusa (aprovação)
     * ou expiração (pagamento); prazo de serviço vencido vira reenvio com backoff e,
     * esgotadas as tentativas, {@code FALHA}.
     */
    public ResultadoSaga verificarPrazo(OrdemServico os, PrazosSaga prazos, OffsetDateTime agora) {
        exigirMesmaOs(os);
        if (prazo == null || prazo.isAfter(agora)) {
            return ResultadoSaga.ignorado("prazo não vencido");
        }
        ResultadoSaga resultado;
        if (estado == EstadoSaga.AGUARDANDO_APROVACAO) {
            resultado = compensarAntesDeCobrar(os, MotivoCancelamento.PRAZO_APROVACAO_VENCIDO, prazos, agora);
        } else if (estado == EstadoSaga.PAGAMENTO_SOLICITADO) {
            resultado = compensarAntesDeCobrar(os, MotivoCancelamento.PAGAMENTO_EXPIRADO, prazos, agora);
        } else if (estado.aguardaRespostaDeServico() && tentativas < prazos.maxTentativas()) {
            tentativas++;
            prazo = agora.plus(prazos.respostaNaTentativa(tentativas));
            resultado = ResultadoSaga.aplicado("reenvio " + tentativas + "/" + prazos.maxTentativas(),
                    List.copyOf(comandosPendentes));
        } else if (estado.aguardaRespostaDeServico()) {
            resultado = falhar("Sem resposta para " + tiposPendentes() + " após "
                    + prazos.maxTentativas() + " reenvio(s)");
        } else {
            prazo = null;
            resultado = ResultadoSaga.ignorado("estado " + estado + " não tem prazo");
        }
        atualizadaEm = agora;
        return resultado;
    }

    // =====================================================================
    // Operação
    // =====================================================================

    /** Volta uma saga em FALHA ao passo em que parou e reenvia o que estava pendente. */
    public ResultadoSaga reprocessar(PrazosSaga prazos, OffsetDateTime agora) {
        if (estado != EstadoSaga.FALHA) {
            throw new TransicaoSagaInvalidaException(
                    "Só sagas em FALHA podem ser reprocessadas (OS " + osId.valor() + " está em " + estado + ")");
        }
        estado = estadoAntesDaFalha;
        estadoAntesDaFalha = null;
        motivoFalha = null;
        tentativas = 0;
        prazo = comandosPendentes.isEmpty() ? null : agora.plus(prazos.resposta());
        atualizadaEm = agora;
        return ResultadoSaga.aplicado("reprocessada a partir de " + estado, List.copyOf(comandosPendentes));
    }

    // =====================================================================
    // Mecânica interna
    // =====================================================================

    private ResultadoSaga solicitar(EstadoSaga novo, ComandoSaga comando, java.time.Duration espera,
                                    OffsetDateTime agora, String descricao) {
        estado = novo;
        tentativas = 0;
        prazo = agora.plus(espera);
        comandosPendentes = new ArrayList<>(List.of(comando));
        return ResultadoSaga.aplicado(descricao, List.of(comando));
    }

    private void aguardarPessoa(EstadoSaga novo) {
        estado = novo;
        tentativas = 0;
        prazo = null;
        comandosPendentes.clear();
    }

    /** Falhas antes do pagamento: libera as peças e cancela o orçamento. Nada a estornar. */
    private ResultadoSaga compensarAntesDeCobrar(OrdemServico os, MotivoCancelamento motivo,
                                                 PrazosSaga prazos, OffsetDateTime agora) {
        return compensar(os, motivo, prazos, agora,
                cancelarOrdemExecucao(os, motivo),
                cancelarOrcamento(os, motivo));
    }

    private ResultadoSaga compensar(OrdemServico os, MotivoCancelamento motivo, PrazosSaga prazos,
                                    OffsetDateTime agora, ComandoSaga... compensacoes) {
        if (os.status().cancelavel()) {
            os.cancelar(motivo, agora);
        }
        estado = EstadoSaga.COMPENSANDO;
        tentativas = 0;
        prazo = agora.plus(prazos.resposta());
        comandosPendentes = new ArrayList<>(List.of(compensacoes));
        return ResultadoSaga.aplicado("compensando: " + motivo, List.of(compensacoes));
    }

    private ResultadoSaga falhar(String motivo) {
        estadoAntesDaFalha = estado;
        estado = EstadoSaga.FALHA;
        motivoFalha = motivo;
        prazo = null;
        return ResultadoSaga.falha(motivo);
    }

    private ResultadoSaga quandoEm(EstadoSaga esperado, java.util.function.Supplier<ResultadoSaga> acao) {
        return estado == esperado ? acao.get() : ResultadoSaga.ignorado("evento não esperado no estado " + estado);
    }

    private void exigirMesmaOs(OrdemServico os) {
        if (!osId.equals(os.id())) {
            throw new IllegalArgumentException("Saga da OS " + osId + " recebeu a OS " + os.id());
        }
    }

    private String tiposPendentes() {
        return comandosPendentes.stream().map(ComandoSaga::tipo).toList().toString();
    }

    private static ComandoSaga cancelarOrdemExecucao(OrdemServico os, MotivoCancelamento motivo) {
        return comando(TiposComando.CANCELAR_ORDEM_EXECUCAO, Destino.EXECUCAO, os, "motivo", motivo.name());
    }

    private static ComandoSaga cancelarOrcamento(OrdemServico os, MotivoCancelamento motivo) {
        return comando(TiposComando.CANCELAR_ORCAMENTO, Destino.BILLING, os,
                "orcamentoId", os.orcamento().orcamentoId().toString(),
                "motivo", motivo.name());
    }

    private static ComandoSaga estornarPagamento(OrdemServico os, MotivoCancelamento motivo) {
        return comando(TiposComando.ESTORNAR_PAGAMENTO, Destino.BILLING, os,
                "orcamentoId", os.orcamento().orcamentoId().toString(),
                "motivo", motivo.name());
    }

    /** Todo comando leva o {@code osId}; os demais campos vêm em pares chave/valor. */
    private static ComandoSaga comando(String tipo, Destino destino, OrdemServico os, Object... campos) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("osId", os.id().valor().toString());
        payload.putAll(mapa(campos));
        return new ComandoSaga(tipo, destino, payload);
    }

    private static Map<String, Object> paraMapa(ItemOS item) {
        return mapa("tipo", item.tipo().name(),
                "referenciaId", item.referenciaId().toString(),
                "descricao", item.descricao(),
                "quantidade", item.quantidade(),
                "valorUnitario", item.valorUnitario().valor().toPlainString());
    }

    private static Map<String, Object> mapa(Object... chaveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < chaveValor.length; i += 2) {
            m.put((String) chaveValor[i], chaveValor[i + 1]);
        }
        return m;
    }

    // ---------- Acessores ----------

    public OrdemServicoId osId()                    { return osId; }
    public EstadoSaga estado()                      { return estado; }
    public EstadoSaga estadoAntesDaFalha()          { return estadoAntesDaFalha; }
    public OffsetDateTime prazo()                   { return prazo; }
    public int tentativas()                         { return tentativas; }
    public List<ComandoSaga> comandosPendentes()    { return Collections.unmodifiableList(comandosPendentes); }
    public String motivoFalha()                     { return motivoFalha; }
    public OffsetDateTime iniciadaEm()              { return iniciadaEm; }
    public OffsetDateTime atualizadaEm()            { return atualizadaEm; }
    public long versao()                            { return versao; }

    /** Saga recém-criada e o primeiro comando a enviar. */
    public record Inicio(SagaOrdemServico saga, List<ComandoSaga> comandos) {}
}
