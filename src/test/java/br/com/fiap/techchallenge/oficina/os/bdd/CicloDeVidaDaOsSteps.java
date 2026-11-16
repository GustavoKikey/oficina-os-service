package br.com.fiap.techchallenge.oficina.os.bdd;

import br.com.fiap.techchallenge.oficina.os.external.api.TokenDeTeste;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Passos do {@code ciclo-de-vida-da-os.feature}.
 *
 * <p>"A Execução faz X" e "o Billing faz Y" viram o evento que aquele serviço publicaria
 * em {@code oficina.eventos}, injetado no canal {@code eventos}. "A Execução recebe" e
 * "o Billing recebe" conferem o comando que o relay do outbox publicou em
 * {@code oficina.comandos}, inclusive a routing key, que é o que decide o destinatário.
 */
public class CicloDeVidaDaOsSteps {

    private static final Duration ESPERA = Duration.ofSeconds(15);
    private static final AtomicInteger PLACAS = new AtomicInteger(10);
    private static final List<String> ETAPAS =
            List.of("o pedido de orçamento", "a aprovação", "o pagamento", "a liberação do reparo");
    private static final Map<String, String> CONFIRMACAO_DA_COMPENSACAO = Map.of(
            "CancelarOrdemExecucao", "OrdemExecucaoCancelada",
            "CancelarOrcamento", "OrcamentoCancelado",
            "EstornarPagamento", "PagamentoEstornado");

    @Inject
    @Any
    InMemoryConnector conector;

    @Inject
    OsEmTeste os;

    // =====================================================================
    // Contexto e fluxo completo
    // =====================================================================

    @Dado("que a cliente {string} chegou à oficina com o veículo {string}")
    public void clienteChegou(String nome, String veiculo) {
        String[] marcaModeloAno = veiculo.split(" ");
        os.registrarCliente(nome, TokenDeTeste.cpfValido());
        int n = PLACAS.getAndIncrement();
        os.registrarVeiculo("BDD%dA%02d".formatted(n % 10, n % 100),
                marcaModeloAno[0], marcaModeloAno[1], Integer.parseInt(marcaModeloAno[2]));
    }

    @Quando("o atendente abre a ordem de serviço com o relato {string}")
    public void abrirOs(String relato) {
        String osId = comoAtendente().contentType(ContentType.JSON).body(os.corpoDaAbertura(relato).encode())
                .post("/ordens-servico")
                .then().statusCode(201).extract().path("id");
        String clienteId = comoAtendente().get("/ordens-servico/" + osId)
                .then().statusCode(200).extract().path("clienteId");
        os.osAberta(osId, clienteId);
    }

    @Entao("a OS fica {string}")
    public void osFica(String status) {
        await().atMost(ESPERA).untilAsserted(() -> comoAtendente().get("/ordens-servico/" + os.osId() + "/status")
                .then().statusCode(200).body("status", equalTo(status)));
    }

    @Entao("a Execução recebe o comando {string} com a placa do veículo")
    public void execucaoRecebeComAPlaca(String tipo) {
        JsonObject comando = esperarComando(tipo, "execucao");
        assertEquals(os.placa(), comando.getJsonObject("payload").getJsonObject("veiculo").getString("placa"));
    }

    @Quando("a Execução coloca a OS na fila e o mecânico inicia o diagnóstico")
    public void diagnosticoIniciado() {
        publicar("OrdemExecucaoAberta", new JsonObject());
        publicar("DiagnosticoIniciado", new JsonObject().put("iniciadoEm", agora()));
    }

    @Quando("o mecânico conclui o diagnóstico com os itens:")
    public void diagnosticoConcluido(DataTable tabela) {
        JsonArray itens = new JsonArray();
        for (Map<String, String> linha : tabela.asMaps()) {
            itens.add(item(linha.get("tipo"), linha.get("descrição"),
                    Integer.parseInt(linha.get("quantidade")), reais(linha.get("valor unitário"))));
        }
        concluirDiagnostico(itens);
    }

    @Entao("o Billing recebe o comando {string} com {int} itens")
    public void billingRecebeComItens(String tipo, int quantidade) {
        assertEquals(quantidade, esperarComando(tipo, "billing").getJsonObject("payload").getJsonArray("itens").size());
    }

    @Quando("o Billing gera o orçamento")
    public void orcamentoGerado() {
        publicar("OrcamentoGerado", new JsonObject()
                .put("orcamentoId", UUID.randomUUID().toString())
                .put("valorTotal", os.valorTotal().toPlainString()));
    }

    @Entao("^a OS fica \"([A-Z_]+)\" com orçamento de R\\$ ([\\d.]+,\\d{2})$")
    public void osFicaComOrcamento(String status, String valor) {
        osFica(status);
        String valorTotal = comoAtendente().get("/ordens-servico/" + os.osId())
                .then().statusCode(200).extract().jsonPath().getString("orcamento.valorTotal");
        assertEquals(0, reais(valor).compareTo(new BigDecimal(valorTotal)),
                () -> "orçamento de " + valorTotal + ", esperado " + valor);
    }

    @Quando("a cliente aprova o orçamento pela área do cliente")
    public void clienteAprova() {
        decidirOrcamento(true).then().statusCode(200);
    }

    @Entao("o Billing recebe o comando {string} aprovado por {string}")
    public void billingRecebeAprovadoPor(String tipo, String quem) {
        assertEquals(quem, esperarComando(tipo, "billing").getJsonObject("payload").getString("aprovadoPor"));
    }

    @Quando("o Billing cria a cobrança no Mercado Pago")
    public void cobrancaCriada() {
        publicar("PagamentoSolicitado", new JsonObject()
                .put("linkPagamento", linkDePagamento())
                .put("expiraEm", OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30).toString()));
    }

    @Entao("a cliente vê o link de pagamento ao consultar a OS")
    public void clienteVeOLink() {
        await().atMost(ESPERA).untilAsserted(() -> comoCliente()
                .get("/cliente/ordens-servico/" + os.osId() + "/status")
                .then().statusCode(200).body("linkPagamento", equalTo(linkDePagamento())));
    }

    @Quando("o Mercado Pago confirma o pagamento")
    public void pagamentoConfirmado() {
        publicar("PagamentoConfirmado", new JsonObject().put("confirmadoEm", agora()));
    }

    @Entao("a Execução recebe o comando {string}")
    public void execucaoRecebe(String tipo) {
        esperarComando(tipo, "execucao");
    }

    @Quando("a Execução libera o reparo e o mecânico o executa até o fim")
    public void reparoExecutado() {
        publicar("ReparoLiberado", new JsonObject().put("liberadoEm", agora()));
        publicar("ReparoIniciado", new JsonObject());
        publicar("ReparoFinalizado", new JsonObject().put("finalizadoEm", agora()));
    }

    @Quando("o atendente entrega o veículo")
    public void entregar() {
        comoAtendente().post("/ordens-servico/" + os.osId() + "/entregar").then().statusCode(200);
    }

    @Entao("a saga termina {string}")
    public void sagaTermina(String estado) {
        await().atMost(ESPERA).untilAsserted(() -> comoAdministrador().get("/admin/sagas/" + os.osId())
                .then().statusCode(200).body("estado", equalTo(estado)));
    }

    @Entao("o histórico da OS mostra {string}")
    public void historico(String sequencia) {
        List<String> esperado = Arrays.stream(sequencia.split(",")).map(String::trim).toList();
        comoAtendente().get("/ordens-servico/" + os.osId() + "/historico")
                .then().statusCode(200).body("transicoes.para", equalTo(esperado));
    }

    // =====================================================================
    // Compensações
    // =====================================================================

    @Dado("^que a OS avançou até (o pedido de orçamento|a aprovação|o pagamento|a liberação do reparo)$")
    public void osAvancouAte(String etapa) {
        int alvo = ETAPAS.indexOf(etapa);

        abrirOs("Barulho na suspensão dianteira");
        esperarComando("AbrirOrdemExecucao", "execucao");
        diagnosticoIniciado();
        concluirDiagnostico(new JsonArray()
                .add(item("SERVICO", "Alinhamento", 1, new BigDecimal("120.00")))
                .add(item("PECA", "Pastilha de freio", 2, new BigDecimal("95.00"))));
        esperarComando("GerarOrcamento", "billing");
        if (alvo == 0) {
            return;
        }
        orcamentoGerado();
        osFica("AGUARDANDO_APROVACAO");
        if (alvo == 1) {
            return;
        }
        comoAtendente().post("/ordens-servico/" + os.osId() + "/orcamento/aprovar").then().statusCode(200);
        esperarComando("SolicitarPagamento", "billing");
        cobrancaCriada();
        clienteVeOLink();
        if (alvo == 2) {
            return;
        }
        pagamentoConfirmado();
        esperarComando("LiberarParaReparo", "execucao");
    }

    @Quando("o Billing não consegue gerar o orçamento")
    public void orcamentoNaoGerado() {
        publicar("OrcamentoNaoGerado", new JsonObject().put("motivo", "Item sem valor unitário"));
    }

    @Quando("a cliente recusa o orçamento")
    public void clienteRecusa() {
        decidirOrcamento(false).then().statusCode(200);
    }

    @Quando("o Mercado Pago recusa o pagamento")
    public void pagamentoRecusado() {
        publicar("PagamentoRecusado", new JsonObject().put("motivo", "cc_rejected_insufficient_amount"));
    }

    @Quando("o link de pagamento vence sem ser pago")
    public void pagamentoExpirado() {
        publicar("PagamentoExpirado", new JsonObject());
    }

    @Quando("a Execução não consegue liberar o reparo")
    public void reparoNaoLiberado() {
        publicar("ReparoNaoLiberado", new JsonObject().put("motivo", "ESTOQUE_INSUFICIENTE"));
    }

    @Entao("a OS fica {string} com o motivo {string}")
    public void osFicaComMotivo(String status, String motivo) {
        osFica(status);
        comoAtendente().get("/ordens-servico/" + os.osId()).then().body("motivoCancelamento", equalTo(motivo));
    }

    /**
     * "Somente": a saga registra as compensações que aguardam confirmação; a lista dela
     * tem de ser exatamente a esperada, e cada uma tem de ter saído para o serviço certo.
     */
    @Entao("a saga dispara somente as compensações {string}")
    public void sagaDisparaCompensacoes(String lista) {
        List<String> esperadas = Arrays.stream(lista.split(",")).map(String::trim).toList();
        await().atMost(ESPERA).untilAsserted(() -> comoAdministrador().get("/admin/sagas/" + os.osId())
                .then().statusCode(200)
                .body("estado", equalTo("COMPENSANDO"))
                .body("comandosPendentes.tipo", containsInAnyOrder(esperadas.toArray())));
        esperadas.forEach(compensacao -> esperarComando(compensacao, destinoDa(compensacao)));
        os.compensacoesDisparadas(esperadas);
    }

    @Quando("os serviços confirmam as compensações")
    public void servicosConfirmam() {
        os.compensacoes().forEach(c -> publicar(CONFIRMACAO_DA_COMPENSACAO.get(c), new JsonObject()));
    }

    // =====================================================================
    // Apoio
    // =====================================================================

    private void concluirDiagnostico(JsonArray itens) {
        os.diagnosticada(itens);
        publicar("DiagnosticoConcluido", new JsonObject().put("concluidoEm", agora()).put("itens", itens.copy()));
    }

    private Response decidirOrcamento(boolean aprovado) {
        return comoCliente().contentType(ContentType.JSON)
                .body(new JsonObject().put("aprovado", aprovado).encode())
                .post("/cliente/ordens-servico/" + os.osId() + "/orcamento/decisao");
    }

    private String linkDePagamento() {
        return "https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=" + os.osId();
    }

    private static String destinoDa(String compensacao) {
        return "CancelarOrdemExecucao".equals(compensacao) ? "execucao" : "billing";
    }

    /** Evento como a Execução ou o Billing o publicariam (envelope de contratos-mensagens.md §2). */
    private void publicar(String tipo, JsonObject payload) {
        boolean doBilling = tipo.startsWith("Orcamento") || tipo.startsWith("Pagamento");
        JsonObject envelope = new JsonObject()
                .put("messageId", UUID.randomUUID().toString())
                .put("type", tipo)
                .put("version", 1)
                .put("correlationId", os.osId())
                .put("occurredAt", agora())
                .put("source", doBilling ? "billing-service" : "execucao-service")
                .put("payload", payload.put("osId", os.osId()));
        conector.<String>source("eventos").send(envelope.encode());
    }

    private JsonObject esperarComando(String tipo, String destino) {
        await().atMost(ESPERA).until(() -> !comandos(tipo, destino).isEmpty());
        return comandos(tipo, destino).get(0);
    }

    private List<JsonObject> comandos(String tipo, String destino) {
        return conector.<String>sink("comandos").received().stream()
                .filter(m -> m.getMetadata(OutgoingRabbitMQMetadata.class)
                        .map(OutgoingRabbitMQMetadata::getRoutingKey)
                        .filter(destino::equals)
                        .isPresent())
                .map(m -> new JsonObject(m.getPayload()))
                .filter(c -> os.osId().equals(c.getString("correlationId")) && tipo.equals(c.getString("type")))
                .toList();
    }

    private static JsonObject item(String tipo, String descricao, int quantidade, BigDecimal valorUnitario) {
        return new JsonObject()
                .put("tipo", tipo)
                .put("referenciaId", UUID.randomUUID().toString())
                .put("descricao", descricao)
                .put("quantidade", quantidade)
                .put("valorUnitario", valorUnitario.toPlainString());
    }

    /** "1.234,56" → 1234.56 (valores escritos como na nota fiscal). */
    private static BigDecimal reais(String valor) {
        return new BigDecimal(valor.replace(".", "").replace(",", "."));
    }

    private static String agora() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private static RequestSpecification comoAtendente() {
        return given().auth().oauth2(TokenDeTeste.paraAtendente()).when();
    }

    private static RequestSpecification comoAdministrador() {
        return given().auth().oauth2(TokenDeTeste.paraAdministrador()).when();
    }

    private RequestSpecification comoCliente() {
        return given().auth().oauth2(TokenDeTeste.paraCliente(os.clienteId())).when();
    }
}
