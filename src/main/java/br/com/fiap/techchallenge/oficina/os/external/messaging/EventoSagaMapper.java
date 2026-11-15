package br.com.fiap.techchallenge.oficina.os.external.messaging;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ItemOS;
import br.com.fiap.techchallenge.oficina.os.saga.entities.EventoSaga;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Traduz o payload JSON de um evento ({@code contratos-mensagens.md} §4) para o tipo do
 * domínio. Fica na borda para que a saga não conheça JSON.
 *
 * <p>Evento de tipo desconhecido não é erro: outros consumidores podem existir e os
 * participantes podem publicar eventos que o orquestrador não usa (regra 3 do
 * contrato). Já um evento conhecido com campo obrigatório ausente é defeito do
 * publicador — vai para a DLQ.
 */
public final class EventoSagaMapper {

    private EventoSagaMapper() {}

    public static Optional<EventoSaga> traduzir(Envelope envelope) {
        JsonNode p = envelope.payload();
        OffsetDateTime quando = envelope.occurredAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : envelope.occurredAt().atOffset(ZoneOffset.UTC);

        EventoSaga evento = switch (envelope.type()) {
            case "OrdemExecucaoAberta" -> new EventoSaga.OrdemExecucaoAberta();
            case "DiagnosticoIniciado" -> new EventoSaga.DiagnosticoIniciado(instante(p, "iniciadoEm", quando));
            case "DiagnosticoConcluido" -> new EventoSaga.DiagnosticoConcluido(
                    instanteOpcional(p, "diagnosticoIniciadoEm"), instante(p, "concluidoEm", quando), itens(p));
            case "ReparoLiberado" -> new EventoSaga.ReparoLiberado(instante(p, "liberadoEm", quando));
            case "ReparoNaoLiberado" -> new EventoSaga.ReparoNaoLiberado(texto(p, "motivo"));
            case "ReparoIniciado" -> new EventoSaga.ReparoIniciado();
            case "ReparoFinalizado" -> new EventoSaga.ReparoFinalizado(instante(p, "finalizadoEm", quando));
            case "OrdemExecucaoCancelada" -> new EventoSaga.OrdemExecucaoCancelada();
            case "OrcamentoGerado" -> new EventoSaga.OrcamentoGerado(
                    uuid(p, "orcamentoId"), Dinheiro.de(obrigatorio(p, "valorTotal").asText()),
                    instante(p, "geradoEm", quando));
            case "OrcamentoNaoGerado" -> new EventoSaga.OrcamentoNaoGerado(texto(p, "motivo"));
            case "PagamentoSolicitado" -> new EventoSaga.PagamentoSolicitado(
                    obrigatorio(p, "linkPagamento").asText(), instanteOpcional(p, "expiraEm"));
            case "PagamentoConfirmado" -> new EventoSaga.PagamentoConfirmado(instante(p, "confirmadoEm", quando));
            case "PagamentoRecusado" -> new EventoSaga.PagamentoRecusado(texto(p, "motivo"));
            case "PagamentoExpirado" -> new EventoSaga.PagamentoExpirado();
            case "OrcamentoCancelado" -> new EventoSaga.OrcamentoCancelado();
            case "PagamentoEstornado" -> new EventoSaga.PagamentoEstornado();
            case "EstornoFalhou" -> new EventoSaga.EstornoFalhou(texto(p, "erro"));
            default -> null;
        };
        return Optional.ofNullable(evento);
    }

    private static List<ItemOS> itens(JsonNode p) {
        JsonNode lista = obrigatorio(p, "itens");
        if (!lista.isArray()) {
            throw new EnvelopeInvalidoException("'itens' deve ser uma lista");
        }
        List<ItemOS> itens = new ArrayList<>();
        for (JsonNode i : lista) {
            try {
                itens.add(new ItemOS(
                        ItemOS.TipoItem.valueOf(obrigatorio(i, "tipo").asText()),
                        uuid(i, "referenciaId"),
                        obrigatorio(i, "descricao").asText(),
                        obrigatorio(i, "quantidade").asInt(),
                        Dinheiro.de(obrigatorio(i, "valorUnitario").asText())));
            } catch (IllegalArgumentException e) {
                throw new EnvelopeInvalidoException("item de diagnóstico inválido: " + e.getMessage());
            }
        }
        return itens;
    }

    private static JsonNode obrigatorio(JsonNode no, String campo) {
        JsonNode valor = no == null ? null : no.get(campo);
        if (valor == null || valor.isNull()) {
            throw new EnvelopeInvalidoException("campo obrigatório ausente no payload: " + campo);
        }
        return valor;
    }

    private static UUID uuid(JsonNode no, String campo) {
        try {
            return UUID.fromString(obrigatorio(no, campo).asText());
        } catch (IllegalArgumentException e) {
            throw new EnvelopeInvalidoException("campo '" + campo + "' não é um UUID");
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no == null ? null : no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    /** Instante do fato; se o publicador não mandar, vale o {@code occurredAt} do envelope. */
    private static OffsetDateTime instante(JsonNode no, String campo, OffsetDateTime padrao) {
        OffsetDateTime valor = instanteOpcional(no, campo);
        return valor == null ? padrao : valor;
    }

    private static OffsetDateTime instanteOpcional(JsonNode no, String campo) {
        String valor = texto(no, campo);
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor);
        } catch (RuntimeException e) {
            throw new EnvelopeInvalidoException("campo '" + campo + "' não é um instante ISO-8601: " + valor);
        }
    }
}
