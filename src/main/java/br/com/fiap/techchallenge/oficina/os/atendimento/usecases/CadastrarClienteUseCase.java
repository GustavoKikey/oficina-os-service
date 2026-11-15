package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteJaCadastradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Documento;

public class CadastrarClienteUseCase {

    private final ClienteGateway repository;

    public CadastrarClienteUseCase(ClienteGateway repository) {
        this.repository = repository;
    }

    public record Input(String nome, String documento, String email, String telefone) {}

    public Cliente executar(Input input) {
        Documento documento = Documento.de(input.documento());
        repository.buscarPorDocumento(documento).ifPresent(c -> {
            throw new ClienteJaCadastradoException(documento.numero());
        });
        Cliente cliente = Cliente.novo(input.nome(), documento, input.email(), input.telefone());
        return repository.salvar(cliente);
    }
}
