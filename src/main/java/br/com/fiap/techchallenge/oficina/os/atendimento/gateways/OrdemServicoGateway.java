package br.com.fiap.techchallenge.oficina.os.atendimento.gateways;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.TransicaoStatus;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoId;

import java.util.List;
import java.util.Optional;

public interface OrdemServicoGateway {

    /** Persiste a OS e acrescenta ao histórico as transições novas do agregado. */
    OrdemServico salvar(OrdemServico os);

    Optional<OrdemServico> buscarPorId(OrdemServicoId id);

    List<OrdemServico> listar(Filtro filtro);

    /** Histórico de status, do mais antigo ao mais recente. */
    List<TransicaoStatus> historico(OrdemServicoId id);

    record Filtro(StatusOS status, ClienteId clienteId, VeiculoId veiculoId) {
        public static Filtro vazio() {
            return new Filtro(null, null, null);
        }
    }
}
