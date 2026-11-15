package br.com.fiap.techchallenge.oficina.os.external.api;

import br.com.fiap.techchallenge.oficina.os.suporte.MensageriaEmMemoria;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A saga do OS Service de ponta a ponta: API HTTP, PostgreSQL, outbox, relay, inbox e
 * consumidor reais; os participantes (Execução e Billing) são simulados pelo conector
 * em memória.
 */
@QuarkusTest
@QuarkusTestResource(value = MensageriaEmMemoria.class, restrictToAnnotatedClass = true)
class SagaFluxoIT {

    private static final Duration ESPERA = Duration.ofSeconds(15);

    @Inject
    @Any
    InMemoryConnector conector;

    private InMemorySink<String> comandos;
    private InMemorySource<String> eventos;

    @BeforeEach
    void canais() {
        comandos = conector.sink("comandos");
        eventos = conector.source("eventos");
    }

    // =====================================================================
    // Cenários
    // =====================================================================

    @Test
    @DisplayName("fluxo feliz: abertura → diagnóstico → orçamento → aprovação pelo cliente → pagamento → reparo → entrega")
    void fluxoFelizDeUmaOs() {
        Os os = abrirOs();
        JsonObject abrir = esperarComando(os.id, "AbrirOrdemExecucao");
        assertEquals("os-service", abrir.getString("source"));
        assertEquals(os.id, abrir.getString("correlationId"));
        assertEquals("ABC" + os.sufixoPlaca, abrir.getJsonObject("payload").getJsonObject("veiculo").getString("placa"));

        levarAteAguardandoAprovacao(os);
        comoAdmin().get("/ordens-servico/" + os.id).then()
                .body("status", equalTo("AGUARDANDO_APROVACAO"))
                .body("itens", hasSize(2))
                .body("orcamento.valorTotal", equalTo(310.0f));

        // o próprio cliente aprova pela área do cliente (JWT emitido pela Lambda)
        comoCliente(os.clienteId).contentType(ContentType.JSON).body("{\"aprovado\": true}")
                .post("/cliente/ordens-servico/" + os.id + "/orcamento/decisao")
                .then().statusCode(200).body("status", equalTo("AGUARDANDO_PAGAMENTO"));
        JsonObject solicitar = esperarComando(os.id, "SolicitarPagamento");
        assertEquals("CLIENTE", solicitar.getJsonObject("payload").getString("aprovadoPor"));

        publicar(os.id, "PagamentoSolicitado", new JsonObject().put("linkPagamento", "https://mp.test/checkout/" + os.id));
        await().atMost(ESPERA).untilAsserted(() -> comoCliente(os.clienteId)
                .get("/cliente/ordens-servico/" + os.id + "/status")
                .then().body("linkPagamento", equalTo("https://mp.test/checkout/" + os.id)));

        publicar(os.id, "PagamentoConfirmado", new JsonObject());
        JsonObject liberar = esperarComando(os.id, "LiberarParaReparo");
        assertNotNull(liberar.getJsonObject("payload").getString("orcamentoId"));

        publicar(os.id, "ReparoLiberado", new JsonObject());
        publicar(os.id, "ReparoIniciado", new JsonObject());
        publicar(os.id, "ReparoFinalizado", new JsonObject());
        esperarStatus(os.id, "FINALIZADA");

        comoAdmin().post("/ordens-servico/" + os.id + "/entregar").then().statusCode(200)
                .body("status", equalTo("ENTREGUE"));

        comoAdmin().get("/ordens-servico/" + os.id + "/historico").then().statusCode(200)
                .body("transicoes.para", equalTo(List.of("RECEBIDA", "EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO",
                        "AGUARDANDO_PAGAMENTO", "EM_EXECUCAO", "FINALIZADA", "ENTREGUE")));

        comoAdmin().get("/admin/sagas/" + os.id).then().statusCode(200)
                .body("estado", equalTo("CONCLUIDA"))
                .body("mensagens.findAll { it.direcao == 'ENVIADA' }.tipo",
                        equalTo(List.of("AbrirOrdemExecucao", "GerarOrcamento", "SolicitarPagamento", "LiberarParaReparo")))
                .body("mensagens.findAll { it.direcao == 'RECEBIDA' }.tipo",
                        hasItems("OrcamentoGerado", "PagamentoConfirmado", "ReparoFinalizado"));

        String metricas = given().get("/q/metrics").then().statusCode(200).extract().asString();
        assertTrue(metricas.contains("oficina_os_abertas_total"));
        assertTrue(metricas.contains("oficina_saga_concluidas_total"));
        assertTrue(metricas.contains("fase=\"Diagnóstico\""), "fase de diagnóstico medida");
        assertTrue(metricas.contains("fase=\"Finalizada\""), "fase de finalização medida");
    }

    @Test
    @DisplayName("falha depois do pagamento: estorna no Billing, libera na Execução e cancela a OS")
    void reparoNaoLiberadoDisparaEstorno() {
        Os os = abrirOs();
        levarAteAguardandoAprovacao(os);
        comoAdmin().post("/ordens-servico/" + os.id + "/orcamento/aprovar").then().statusCode(200);
        esperarComando(os.id, "SolicitarPagamento");
        publicar(os.id, "PagamentoConfirmado", new JsonObject());
        esperarComando(os.id, "LiberarParaReparo");

        publicar(os.id, "ReparoNaoLiberado", new JsonObject().put("motivo", "ESTOQUE_INSUFICIENTE"));

        esperarComando(os.id, "EstornarPagamento");
        esperarComando(os.id, "CancelarOrdemExecucao");
        comoAdmin().get("/ordens-servico/" + os.id).then()
                .body("status", equalTo("CANCELADA"))
                .body("motivoCancelamento", equalTo("REPARO_NAO_LIBERADO"));
        comoAdmin().get("/admin/sagas/" + os.id).then().body("estado", equalTo("COMPENSANDO"))
                .body("comandosPendentes.tipo", hasItems("EstornarPagamento", "CancelarOrdemExecucao"));

        publicar(os.id, "PagamentoEstornado", new JsonObject());
        publicar(os.id, "OrdemExecucaoCancelada", new JsonObject());
        await().atMost(ESPERA).untilAsserted(() -> comoAdmin().get("/admin/sagas/" + os.id)
                .then().body("estado", equalTo("COMPENSADA")));
        assertTrue(given().get("/q/metrics").asString().contains("motivo=\"REPARO_NAO_LIBERADO\""));
    }

    @Test
    @DisplayName("o cliente curioso: Bruno não vê nem decide a OS da Alice")
    void clienteSoAlcancaAsProprias() {
        Os daAlice = abrirOs();
        levarAteAguardandoAprovacao(daAlice);
        String bruno = UUID.randomUUID().toString();

        comoCliente(bruno).get("/cliente/ordens-servico/" + daAlice.id).then().statusCode(403);
        comoCliente(bruno).contentType(ContentType.JSON).body("{\"aprovado\": true}")
                .post("/cliente/ordens-servico/" + daAlice.id + "/orcamento/decisao").then().statusCode(403);
        comoCliente(bruno).get("/cliente/ordens-servico").then().statusCode(200).body("$", hasSize(0));
        comoCliente(daAlice.clienteId).get("/cliente/ordens-servico").then().statusCode(200)
                .body("id", hasItems(daAlice.id));

        comoAdmin().get("/admin/sagas/" + daAlice.id).then().body("estado", equalTo("AGUARDANDO_APROVACAO"));
    }

    @Test
    @DisplayName("cliente recusa: compensa os dois participantes")
    void recusaCompensa() {
        Os os = abrirOs();
        levarAteAguardandoAprovacao(os);

        comoCliente(os.clienteId).contentType(ContentType.JSON).body("{\"aprovado\": false}")
                .post("/cliente/ordens-servico/" + os.id + "/orcamento/decisao")
                .then().statusCode(200).body("status", equalTo("CANCELADA"));

        esperarComando(os.id, "CancelarOrdemExecucao");
        esperarComando(os.id, "CancelarOrcamento");
        comoAdmin().post("/ordens-servico/" + os.id + "/orcamento/aprovar").then().statusCode(409);
    }

    @Test
    @DisplayName("evento repetido (mesmo messageId) é aplicado uma vez só")
    void eventoDuplicadoNaoDuplicaComando() {
        Os os = abrirOs();
        esperarComando(os.id, "AbrirOrdemExecucao");
        publicar(os.id, "OrdemExecucaoAberta", new JsonObject());
        JsonObject concluido = envelope(os.id, "DiagnosticoConcluido", diagnostico());

        eventos.send(concluido.encode());
        eventos.send(concluido.encode());

        esperarComando(os.id, "GerarOrcamento");
        await().pollDelay(Duration.ofSeconds(2)).atMost(ESPERA).until(() -> true);
        assertEquals(1, comandosDa(os.id, "GerarOrcamento").size());
    }

    @Test
    void operacoesForaDeHoraOuInexistentes() {
        Os os = abrirOs();

        comoAdmin().post("/ordens-servico/" + os.id + "/orcamento/aprovar").then().statusCode(409);
        comoAdmin().post("/ordens-servico/" + os.id + "/entregar").then().statusCode(409);
        comoAdmin().post("/admin/sagas/" + os.id + "/reprocessar").then().statusCode(409);
        comoAdmin().get("/admin/sagas/" + UUID.randomUUID()).then().statusCode(404);
        comoAdmin().get("/ordens-servico/" + UUID.randomUUID() + "/historico").then().statusCode(404);
        comoAdmin().get("/ordens-servico?status=RECEBIDA").then().statusCode(200).body("id", hasItems(os.id));
        comoAdmin().get("/ordens-servico/" + os.id + "/status").then().body("descricao", equalTo("Recebida"));
        given().get("/admin/sagas/" + os.id).then().statusCode(401);
    }

    // =====================================================================
    // Apoio
    // =====================================================================

    private record Os(String id, String clienteId, String sufixoPlaca) {}

    private static int sequencia = 100;

    private Os abrirOs() {
        String sufixo = "1D" + (sequencia++ % 90 + 10);
        String cpf = TokenDeTeste.cpfValido();
        String id = comoAdmin().contentType(ContentType.JSON).body("""
                        {"cliente": {"documento": "%s", "nome": "Alice Souza", "email": "alice@exemplo.com"},
                         "veiculo": {"placa": "ABC%s", "marca": "Fiat", "modelo": "Argo", "ano": 2022},
                         "relatoCliente": "Barulho na suspensão"}
                        """.formatted(cpf, sufixo))
                .post("/ordens-servico")
                .then().statusCode(201).body("status", equalTo("RECEBIDA"))
                .extract().path("id");
        String clienteId = comoAdmin().get("/ordens-servico/" + id).then().extract().path("clienteId");
        return new Os(id, clienteId, sufixo);
    }

    private void levarAteAguardandoAprovacao(Os os) {
        esperarComando(os.id, "AbrirOrdemExecucao");
        publicar(os.id, "OrdemExecucaoAberta", new JsonObject());
        publicar(os.id, "DiagnosticoIniciado", new JsonObject());
        publicar(os.id, "DiagnosticoConcluido", diagnostico());
        JsonObject gerar = esperarComando(os.id, "GerarOrcamento");
        assertEquals(2, gerar.getJsonObject("payload").getJsonArray("itens").size());
        publicar(os.id, "OrcamentoGerado", new JsonObject()
                .put("orcamentoId", UUID.randomUUID().toString())
                .put("valorTotal", "310.00"));
        esperarStatus(os.id, "AGUARDANDO_APROVACAO");
    }

    private static JsonObject diagnostico() {
        return new JsonObject()
                .put("concluidoEm", OffsetDateTime.now(ZoneOffset.UTC).toString())
                .put("itens", new JsonArray()
                        .add(new JsonObject().put("tipo", "SERVICO").put("referenciaId", UUID.randomUUID().toString())
                                .put("descricao", "Alinhamento").put("quantidade", 1).put("valorUnitario", "120.00"))
                        .add(new JsonObject().put("tipo", "PECA").put("referenciaId", UUID.randomUUID().toString())
                                .put("descricao", "Pastilha de freio").put("quantidade", 2).put("valorUnitario", "95.00")));
    }

    private static JsonObject envelope(String osId, String tipo, JsonObject payload) {
        return new JsonObject()
                .put("messageId", UUID.randomUUID().toString())
                .put("type", tipo)
                .put("version", 1)
                .put("correlationId", osId)
                .put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString())
                .put("source", tipo.startsWith("Orcamento") || tipo.startsWith("Pagamento") ? "billing-service" : "execucao-service")
                .put("payload", payload.put("osId", osId));
    }

    private void publicar(String osId, String tipo, JsonObject payload) {
        eventos.send(envelope(osId, tipo, payload).encode());
    }

    private List<JsonObject> comandosDa(String osId, String tipo) {
        return comandos.received().stream()
                .map(Message::getPayload)
                .map(JsonObject::new)
                .filter(c -> osId.equals(c.getString("correlationId")) && tipo.equals(c.getString("type")))
                .toList();
    }

    private JsonObject esperarComando(String osId, String tipo) {
        await().atMost(ESPERA).until(() -> !comandosDa(osId, tipo).isEmpty());
        return comandosDa(osId, tipo).get(0);
    }

    private void esperarStatus(String osId, String status) {
        await().atMost(ESPERA).untilAsserted(() -> comoAdmin().get("/ordens-servico/" + osId + "/status")
                .then().body("status", equalTo(status)));
    }

    private static RequestSpecification comoAdmin() {
        return given().auth().oauth2(TokenDeTeste.paraAdministrador()).when();
    }

    private static RequestSpecification comoCliente(String clienteId) {
        return given().auth().oauth2(TokenDeTeste.paraCliente(clienteId)).when();
    }
}
