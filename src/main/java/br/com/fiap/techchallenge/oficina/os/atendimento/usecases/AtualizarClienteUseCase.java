package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;

public class AtualizarClienteUseCase {

    private final ClienteGateway repository;

    public AtualizarClienteUseCase(ClienteGateway repository) {
        this.repository = repository;
    }

    public record Input(ClienteId id, String nome, String email, String telefone) {}

    public Cliente executar(Input input) {
        Cliente cliente = repository.buscarPorId(input.id())
                .orElseThrow(() -> new ClienteNaoEncontradoException(input.id()));
        cliente.renomear(input.nome());
        cliente.alterarContato(input.email(), input.telefone());
        return repository.salvar(cliente);
    }
}
