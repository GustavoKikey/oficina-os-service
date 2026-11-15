package br.com.fiap.techchallenge.oficina.os.external.messaging;

/**
 * Mensagem que não pode ser processada por defeito de formato — não por regra de
 * negócio. Vai para a DLQ: reprocessar não a tornaria válida.
 */
public class EnvelopeInvalidoException extends RuntimeException {

    public EnvelopeInvalidoException(String mensagem) {
        super(mensagem);
    }
}
