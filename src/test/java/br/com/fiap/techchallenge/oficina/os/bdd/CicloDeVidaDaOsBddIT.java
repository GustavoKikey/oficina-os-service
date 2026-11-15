package br.com.fiap.techchallenge.oficina.os.bdd;

import br.com.fiap.techchallenge.oficina.os.suporte.MensageriaEmMemoria;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;

/**
 * BDD do fluxo completo da OS: roda os cenários de {@code features/} dentro de um
 * {@code @QuarkusTest} (a aplicação inteira no ar, com PostgreSQL do Testcontainers e os
 * canais RabbitMQ trocados pelo conector em memória).
 *
 * <p>Relatórios: {@code target/cucumber/ciclo-de-vida-da-os.html} (legível) e {@code .json}.
 */
@QuarkusTestResource(value = MensageriaEmMemoria.class, restrictToAnnotatedClass = true)
@CucumberOptions(
        features = "classpath:features",
        glue = "br.com.fiap.techchallenge.oficina.os.bdd",
        plugin = {
                "html:target/cucumber/ciclo-de-vida-da-os.html",
                "json:target/cucumber/ciclo-de-vida-da-os.json"
        })
public class CicloDeVidaDaOsBddIT extends CucumberQuarkusTest {

    /** Permite rodar um .feature direto da IDE. */
    public static void main(String[] args) {
        runMain(CicloDeVidaDaOsBddIT.class, args);
    }
}
