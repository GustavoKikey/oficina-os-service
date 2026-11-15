package br.com.fiap.techchallenge.oficina.os.atendimento.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Abertura de OS. Na fase 4 não há itens: serviços e peças nascem no diagnóstico,
 * registrados pelo mecânico no Execução Service (ADR 006).
 */
public record AbrirOrdemServicoRequest(
        @NotNull @Valid ClienteInput cliente,
        @NotNull @Valid VeiculoInput veiculo,
        @Schema(example = "Barulho na suspensão dianteira ao passar em lombadas",
                description = "O que o cliente relatou no balcão — vai para o mecânico na fila de diagnóstico")
        @Size(max = 500) String relatoCliente
) {

    public record ClienteInput(
            @Schema(example = "52998224725", description = "Documento (CPF/CNPJ) do cliente")
            @NotBlank String documento,
            @Schema(example = "Ana Souza", description = "Nome — obrigatório se o cliente ainda não existir")
            String nome,
            @Schema(example = "ana@email.com", description = "E-mail para notificações de status da OS")
            String email,
            @Schema(example = "11999990000")
            String telefone
    ) {}

    public record VeiculoInput(
            @Schema(example = "ABC1D23", description = "Placa do veículo")
            @NotBlank String placa,
            @Schema(example = "VW", description = "Marca — obrigatória se o veículo ainda não existir")
            String marca,
            @Schema(example = "Golf", description = "Modelo — obrigatório se o veículo ainda não existir")
            String modelo,
            @Schema(example = "2020", description = "Ano — obrigatório se o veículo ainda não existir")
            Integer ano
    ) {}
}
