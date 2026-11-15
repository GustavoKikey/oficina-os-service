package br.com.fiap.techchallenge.oficina.os.atendimento.gateways;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Veiculo;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoId;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Placa;

import java.util.List;
import java.util.Optional;

public interface VeiculoGateway {

    Veiculo salvar(Veiculo veiculo);

    Optional<Veiculo> buscarPorId(VeiculoId id);

    Optional<Veiculo> buscarPorPlaca(Placa placa);

    List<Veiculo> listarPorCliente(ClienteId clienteId);

    List<Veiculo> listar();

    void remover(VeiculoId id);
}
