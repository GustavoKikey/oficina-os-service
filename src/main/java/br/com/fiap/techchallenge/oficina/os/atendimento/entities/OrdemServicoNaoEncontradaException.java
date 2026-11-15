package br.com.fiap.techchallenge.oficina.os.atendimento.entities;

import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;

public class OrdemServicoNaoEncontradaException extends DomainException {

    public OrdemServicoNaoEncontradaException(OrdemServicoId id) {
        super("Ordem de Serviço não encontrada: " + id);
    }
}
