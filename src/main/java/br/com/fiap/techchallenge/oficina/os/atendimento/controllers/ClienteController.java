package br.com.fiap.techchallenge.oficina.os.atendimento.controllers;

import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.AtualizarClienteRequest;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.ClienteAutenticacaoResponse;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Documento;
import java.util.Optional;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.CadastrarClienteRequest;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.ClienteResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.presenters.ClientePresenter;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.AtualizarClienteUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.BuscarClientePorIdUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.CadastrarClienteUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.ListarClientesUseCase;
import br.com.fiap.techchallenge.oficina.os.atendimento.usecases.RemoverClienteUseCase;
import br.com.fiap.techchallenge.oficina.os.shared.usecases.ExecutorTransacional;

import java.util.List;
import java.util.UUID;

/** Controller do agregado Cliente — orquestra os use cases sob a porta de transação. */
public class ClienteController {

    private final ClienteGateway gateway;
    private final CadastrarClienteUseCase cadastrar;
    private final AtualizarClienteUseCase atualizar;
    private final RemoverClienteUseCase remover;
    private final BuscarClientePorIdUseCase buscar;
    private final ListarClientesUseCase listar;
    private final ExecutorTransacional tx;
    private final ClientePresenter presenter = new ClientePresenter();

    public ClienteController(ClienteGateway gateway, ExecutorTransacional tx) {
        this.gateway = gateway;
        this.cadastrar = new CadastrarClienteUseCase(gateway);
        this.atualizar = new AtualizarClienteUseCase(gateway);
        this.remover = new RemoverClienteUseCase(gateway);
        this.buscar = new BuscarClientePorIdUseCase(gateway);
        this.listar = new ListarClientesUseCase(gateway);
        this.tx = tx;
    }

    public ClienteResponse cadastrar(CadastrarClienteRequest req) {
        Cliente c = tx.emTransacao(() -> cadastrar.executar(new CadastrarClienteUseCase.Input(
                req.nome(), req.documento(), req.email(), req.telefone())));
        return presenter.apresentar(c);
    }

    public List<ClienteResponse> listar() {
        return presenter.apresentar(tx.emTransacao(listar::executar));
    }

    public ClienteResponse buscar(UUID id) {
        return presenter.apresentar(tx.emTransacao(() -> buscar.executar(ClienteId.de(id))));
    }

    public ClienteResponse atualizar(UUID id, AtualizarClienteRequest req) {
        Cliente c = tx.emTransacao(() -> atualizar.executar(new AtualizarClienteUseCase.Input(
                ClienteId.de(id), req.nome(), req.email(), req.telefone())));
        return presenter.apresentar(c);
    }

    /** Consulta da Lambda de autenticação (ADR 011): inclui clientes inativos — ela decide. */
    public Optional<ClienteAutenticacaoResponse> buscarParaAutenticacao(String documento) {
        Documento doc = Documento.de(documento);
        return tx.emTransacao(() -> gateway.buscarPorDocumento(doc))
                .map(c -> new ClienteAutenticacaoResponse(c.id().valor(), c.nome(), c.ativo()));
    }

    public void remover(UUID id) {
        tx.emTransacao(() -> {
            remover.executar(ClienteId.de(id));
            return null;
        });
    }
}
