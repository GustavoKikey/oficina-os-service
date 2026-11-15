package br.com.fiap.techchallenge.oficina.os.saga.entities;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Comando que a saga manda a um participante ({@code docs/fase-4/contratos-mensagens.md} §3).
 *
 * <p>O payload é um mapa de valores simples (texto, número, listas e mapas) — a camada
 * de mensageria o serializa em JSON. Assim o domínio não depende de Jackson.
 * Dinheiro vai como texto decimal ({@code "350.00"}) e instantes em ISO-8601.
 */
public record ComandoSaga(String tipo, Destino destino, Map<String, Object> payload) {

    /** Serviço que recebe o comando. O valor é a routing key no exchange {@code oficina.comandos}. */
    public enum Destino {
        EXECUCAO("execucao"),
        BILLING("billing");

        private final String routingKey;

        Destino(String routingKey) {
            this.routingKey = routingKey;
        }

        public String routingKey() {
            return routingKey;
        }
    }

    public ComandoSaga {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(destino, "destino");
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /** Evento que o participante publica para confirmar este comando de compensação. */
    String confirmacaoEsperada() {
        return switch (tipo) {
            case TiposComando.CANCELAR_ORDEM_EXECUCAO -> "OrdemExecucaoCancelada";
            case TiposComando.CANCELAR_ORCAMENTO -> "OrcamentoCancelado";
            case TiposComando.ESTORNAR_PAGAMENTO -> "PagamentoEstornado";
            default -> null;
        };
    }

    /** Nomes dos comandos, exatamente como no contrato. */
    public static final class TiposComando {
        public static final String ABRIR_ORDEM_EXECUCAO = "AbrirOrdemExecucao";
        public static final String LIBERAR_PARA_REPARO = "LiberarParaReparo";
        public static final String CANCELAR_ORDEM_EXECUCAO = "CancelarOrdemExecucao";
        public static final String GERAR_ORCAMENTO = "GerarOrcamento";
        public static final String SOLICITAR_PAGAMENTO = "SolicitarPagamento";
        public static final String CANCELAR_ORCAMENTO = "CancelarOrcamento";
        public static final String ESTORNAR_PAGAMENTO = "EstornarPagamento";

        private TiposComando() {}
    }
}
