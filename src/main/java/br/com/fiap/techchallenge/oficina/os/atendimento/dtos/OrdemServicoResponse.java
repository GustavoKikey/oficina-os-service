package br.com.fiap.techchallenge.oficina.os.atendimento.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrdemServicoResponse(
        UUID id,
        UUID clienteId,
        UUID veiculoId,
        String status,
        String descricaoStatus,
        String relatoCliente,
        List<ItemView> itens,
        OrcamentoView orcamento,
        PagamentoView pagamento,
        String motivoCancelamento,
        OffsetDateTime criadaEm,
        OffsetDateTime diagnosticoIniciadoEm,
        OffsetDateTime execucaoIniciadaEm,
        OffsetDateTime finalizadaEm,
        OffsetDateTime entregueEm,
        OffsetDateTime canceladaEm
) {
    /** Item do diagnóstico, como registrado pelo mecânico na Execução. */
    public record ItemView(String tipo, UUID referenciaId, String descricao, int quantidade,
                           BigDecimal valorUnitario, BigDecimal subtotal) {}

    /** Resumo do orçamento — o completo está no Billing. */
    public record OrcamentoView(UUID orcamentoId, BigDecimal valorTotal, OffsetDateTime geradoEm,
                                OffsetDateTime aprovadoEm, String aprovadoPor) {}

    /** Link de cobrança do Mercado Pago e confirmação do pagamento. */
    public record PagamentoView(String link, OffsetDateTime expiraEm, OffsetDateTime confirmadoEm) {}
}
