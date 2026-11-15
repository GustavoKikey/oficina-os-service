package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import java.time.OffsetDateTime;

/**
 * Situação do pagamento vista pela OS: o link de cobrança do Mercado Pago (para o
 * cliente pagar) e a confirmação. Quem fala com o Mercado Pago é o Billing.
 *
 * @param link         nulo até o Billing criar a cobrança
 * @param confirmadoEm nulo até o pagamento ser confirmado
 */
public record Pagamento(String link, OffsetDateTime expiraEm, OffsetDateTime confirmadoEm) {

    public static final Pagamento NENHUM = new Pagamento(null, null, null);

    Pagamento comLink(String novoLink, OffsetDateTime novaExpiracao) {
        return new Pagamento(novoLink, novaExpiracao, confirmadoEm);
    }

    Pagamento confirmado(OffsetDateTime em) {
        return new Pagamento(link, expiraEm, em);
    }

    public boolean confirmado() {
        return confirmadoEm != null;
    }
}
