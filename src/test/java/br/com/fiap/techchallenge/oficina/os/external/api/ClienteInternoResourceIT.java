package br.com.fiap.techchallenge.oficina.os.external.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/** Rota interna da Lambda de CPF (ADR 011). O token de teste vem do perfil %test. */
@QuarkusTest
class ClienteInternoResourceIT {

    private static final String TOKEN = "token-interno-teste";

    @Test
    @DisplayName("sem o segredo compartilhado a rota interna não responde nada")
    void exigeOToken() {
        given().queryParam("documento", "52998224725").get("/internal/clientes").then().statusCode(403);
        given().header("X-Internal-Token", "errado").queryParam("documento", "52998224725")
                .get("/internal/clientes").then().statusCode(403);
    }

    @Test
    void devolveSoOQueALambdaPrecisa() {
        String cpf = TokenDeTeste.cpfValido();
        given().auth().oauth2(TokenDeTeste.paraAdministrador()).contentType(ContentType.JSON)
                .body("{\"nome\": \"Carla Dias\", \"documento\": \"%s\"}".formatted(cpf))
                .post("/clientes").then().statusCode(201);

        given().header("X-Internal-Token", TOKEN).queryParam("documento", cpf)
                .get("/internal/clientes")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("nome", equalTo("Carla Dias"))
                .body("ativo", equalTo(true));
    }

    @Test
    void clienteInexistenteOuSemDocumento() {
        given().header("X-Internal-Token", TOKEN).queryParam("documento", TokenDeTeste.cpfValido())
                .get("/internal/clientes").then().statusCode(404);
        given().header("X-Internal-Token", TOKEN).get("/internal/clientes").then().statusCode(404);
        given().header("X-Internal-Token", TOKEN).queryParam("documento", "123")
                .get("/internal/clientes").then().statusCode(400);
    }

    @Test
    void ficaForaDoContratoPublico() {
        String openapi = given().get("/openapi").then().statusCode(200).extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(openapi.contains("/internal/clientes"));
    }
}
