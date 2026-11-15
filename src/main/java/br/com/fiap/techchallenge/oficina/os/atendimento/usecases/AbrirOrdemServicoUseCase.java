package br.com.fiap.techchallenge.oficina.os.atendimento.usecases;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Cliente;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Veiculo;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.ClienteGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.OrdemServicoGateway;
import br.com.fiap.techchallenge.oficina.os.atendimento.gateways.VeiculoGateway;
import br.com.fiap.techchallenge.oficina.os.saga.entities.PrazosSaga;
import br.com.fiap.techchallenge.oficina.os.saga.entities.SagaOrdemServico;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.OutboxGateway;
import br.com.fiap.techchallenge.oficina.os.saga.gateways.SagaGateway;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Documento;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Placa;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Abertura da OS — o primeiro passo da saga.
 *
 * <p>Cliente e veículo novos são cadastrados na hora (regra da fase 1). Na mesma
 * transação nascem a OS, a saga e o comando {@code AbrirOrdemExecucao} no outbox: ou a
 * OS existe <b>e</b> a Execução vai recebê-la, ou nada aconteceu.
 *
 * <p>Diferente da fase 3, a abertura não recebe itens: eles nascem no diagnóstico, na
 * Execução (ADR 006). O que o cliente contou no balcão vai em {@code relatoCliente}.
 */
public class AbrirOrdemServicoUseCase {

    private final OrdemServicoGateway ordens;
    private final ClienteGateway clientes;
    private final VeiculoGateway veiculos;
    private final SagaGateway sagas;
    private final OutboxGateway outbox;
    private final PrazosSaga prazos;
    private final Clock relogio;

    public AbrirOrdemServicoUseCase(OrdemServicoGateway ordens, ClienteGateway clientes, VeiculoGateway veiculos,
                                    SagaGateway sagas, OutboxGateway outbox, PrazosSaga prazos, Clock relogio) {
        this.ordens = ordens;
        this.clientes = clientes;
        this.veiculos = veiculos;
        this.sagas = sagas;
        this.outbox = outbox;
        this.prazos = prazos;
        this.relogio = relogio;
    }

    public record DadosCliente(String documento, String nome, String email, String telefone) {}
    public record DadosVeiculo(String placa, String marca, String modelo, Integer ano) {}
    public record Input(DadosCliente cliente, DadosVeiculo veiculo, String relatoCliente) {}

    public OrdemServico executar(Input input) {
        Cliente cliente = resolverCliente(input.cliente());
        Veiculo veiculo = resolverVeiculo(input.veiculo(), cliente);
        OffsetDateTime agora = OffsetDateTime.now(relogio);

        OrdemServico os = OrdemServico.abrir(cliente.id(), veiculo.id(), input.relatoCliente(), agora);
        SagaOrdemServico.Inicio inicio = SagaOrdemServico.iniciar(os, veiculo, prazos, agora);

        OrdemServico salva = ordens.salvar(os);
        sagas.salvar(inicio.saga());
        outbox.enfileirar(inicio.comandos(), os.id(), null);
        return salva;
    }

    private Cliente resolverCliente(DadosCliente dados) {
        Documento documento = Documento.de(dados.documento());
        return clientes.buscarPorDocumento(documento).orElseGet(() -> {
            if (dados.nome() == null || dados.nome().isBlank()) {
                throw new ClienteNaoEncontradoException(
                        "Cliente não encontrado para o documento " + documento.numero()
                                + " — informe o nome para cadastrá-lo na abertura da OS");
            }
            return clientes.salvar(Cliente.novo(dados.nome(), documento, dados.email(), dados.telefone()));
        });
    }

    private Veiculo resolverVeiculo(DadosVeiculo dados, Cliente cliente) {
        Placa placa = Placa.de(dados.placa());
        return veiculos.buscarPorPlaca(placa).orElseGet(() -> {
            if (dados.marca() == null || dados.modelo() == null || dados.ano() == null) {
                throw new VeiculoNaoEncontradoException(
                        "Veículo não encontrado para a placa " + placa.valor()
                                + " — informe marca, modelo e ano para cadastrá-lo na abertura da OS");
            }
            return veiculos.salvar(Veiculo.novo(placa, dados.marca(), dados.modelo(), dados.ano(), cliente.id()));
        });
    }
}
