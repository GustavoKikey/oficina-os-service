package br.com.fiap.techchallenge.oficina.os;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

/**
 * Sobe o serviço inteiro contra PostgreSQL e RabbitMQ reais (Dev Services) e confere
 * que ele está pronto: banco conectado, migrações aplicadas e conector de mensageria
 * ligado à fila {@code os.eventos}.
 */
@QuarkusTest
class SaudeTest {

    @Test
    void servicoFicaProntoComBancoEMensageria() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItems(
                        "Database connections health check",
                        "SmallRye Reactive Messaging - readiness check"));
    }

    @Test
    void publicaContratoOpenApi() {
        given()
                .when().get("/openapi")
                .then()
                .statusCode(200);
    }
}
