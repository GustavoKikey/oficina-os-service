package br.com.fiap.techchallenge.oficina.os.relatorio.dtos;

/** Record puro de resposta; a montagem vive no {@code RelatorioPresenter}. */
public record TempoMedioResponse(long amostra, long tempoMedioExecucaoSegundos) {
}
