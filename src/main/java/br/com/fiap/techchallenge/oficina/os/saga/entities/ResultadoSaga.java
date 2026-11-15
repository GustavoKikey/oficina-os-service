package br.com.fiap.techchallenge.oficina.os.saga.entities;

import java.util.List;

/**
 * O que uma transição da saga produziu.
 *
 * @param aplicado  {@code false} quando o evento não cabia no estado (duplicado,
 *                  atrasado, de passo superado) — nada mudou e nada é enviado
 * @param comandos  comandos a gravar no outbox, na mesma transação
 * @param falhou    a saga entrou em {@code FALHA} — dispara alerta
 * @param descricao texto para log e auditoria
 */
public record ResultadoSaga(boolean aplicado, List<ComandoSaga> comandos, boolean falhou, String descricao) {

    public ResultadoSaga {
        comandos = List.copyOf(comandos);
    }

    static ResultadoSaga aplicado(String descricao) {
        return new ResultadoSaga(true, List.of(), false, descricao);
    }

    static ResultadoSaga aplicado(String descricao, List<ComandoSaga> comandos) {
        return new ResultadoSaga(true, comandos, false, descricao);
    }

    static ResultadoSaga ignorado(String descricao) {
        return new ResultadoSaga(false, List.of(), false, descricao);
    }

    static ResultadoSaga falha(String descricao) {
        return new ResultadoSaga(true, List.of(), true, descricao);
    }
}
