package br.com.fiap.techchallenge.oficina.os.atendimento.gateways;

import java.time.Duration;

/**
 * Porta de métricas de negócio. As entidades e use cases declaram o que vale medir sem
 * importar Micrometer; o adapter é <i>best-effort</i> — falha de telemetria nunca
 * derruba operação de negócio (fase 3).
 */
public interface MetricasGateway {

    void ordemServicoAberta();

    void faseConcluida(String fase, Duration duracao);

    void falhaProcessamento(String operacao);

    void falhaIntegracao(String integracao);

    // ----- Saga (fase 4) -----

    void sagaConcluida();

    void sagaCompensada(String motivo);

    void sagaFalhou(String passo);
}
