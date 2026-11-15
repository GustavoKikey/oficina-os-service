package br.com.fiap.techchallenge.oficina.os.suporte;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.HashMap;
import java.util.Map;

/**
 * Troca os canais RabbitMQ pelo conector em memória do SmallRye: o teste vê cada
 * comando que o relay publica ({@code comandos}) e injeta eventos como se fosse a
 * Execução ou o Billing ({@code eventos}). O resto — HTTP, banco, outbox, inbox,
 * saga — é o código real.
 */
public class MensageriaEmMemoria implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Map<String, String> propriedades = new HashMap<>();
        propriedades.putAll(InMemoryConnector.switchIncomingChannelsToInMemory("eventos"));
        propriedades.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("comandos"));
        return propriedades;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
