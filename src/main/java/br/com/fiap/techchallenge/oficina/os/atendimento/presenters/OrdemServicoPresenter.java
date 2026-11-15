package br.com.fiap.techchallenge.oficina.os.atendimento.presenters;

import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.AberturaOrdemServicoResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.HistoricoOSResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.OrdemServicoResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.StatusOSResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ItemOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Pagamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.TransicaoStatus;

import java.util.List;

public class OrdemServicoPresenter {

    public OrdemServicoResponse apresentar(OrdemServico os) {
        return new OrdemServicoResponse(
                os.id().valor(),
                os.clienteId().valor(),
                os.veiculoId().valor(),
                os.status().name(),
                os.status().descricao(),
                os.relatoCliente(),
                os.itens().stream().map(this::view).toList(),
                os.orcamento() == null ? null : view(os.orcamento()),
                view(os.pagamento()),
                os.motivoCancelamento() == null ? null : os.motivoCancelamento().name(),
                os.criadaEm(),
                os.diagnosticoIniciadoEm(),
                os.execucaoIniciadaEm(),
                os.finalizadaEm(),
                os.entregueEm(),
                os.canceladaEm());
    }

    public List<OrdemServicoResponse> apresentar(List<OrdemServico> ordens) {
        return ordens.stream().map(this::apresentar).toList();
    }

    public AberturaOrdemServicoResponse apresentarAbertura(OrdemServico os) {
        return new AberturaOrdemServicoResponse(os.id().valor(), os.status().name(), os.status().descricao(),
                os.criadaEm());
    }

    public StatusOSResponse apresentarStatus(OrdemServico os) {
        return new StatusOSResponse(os.id().valor(), os.status().name(), os.status().descricao(),
                os.atualizadaEm(), os.pagamento().link());
    }

    public HistoricoOSResponse apresentarHistorico(OrdemServico os, List<TransicaoStatus> transicoes) {
        return new HistoricoOSResponse(os.id().valor(), os.status().name(),
                transicoes.stream().map(t -> new HistoricoOSResponse.TransicaoView(
                        t.de() == null ? null : t.de().name(),
                        t.para().name(),
                        t.para().descricao(),
                        t.em(),
                        t.motivo())).toList());
    }

    private OrdemServicoResponse.ItemView view(ItemOS i) {
        return new OrdemServicoResponse.ItemView(i.tipo().name(), i.referenciaId(), i.descricao(), i.quantidade(),
                i.valorUnitario().valor(), i.subtotal().valor());
    }

    private OrdemServicoResponse.OrcamentoView view(ResumoOrcamento o) {
        return new OrdemServicoResponse.OrcamentoView(o.orcamentoId(), o.valorTotal().valor(), o.geradoEm(),
                o.aprovadoEm(), o.aprovadoPor() == null ? null : o.aprovadoPor().name());
    }

    private OrdemServicoResponse.PagamentoView view(Pagamento p) {
        if (p.link() == null && p.confirmadoEm() == null) {
            return null;
        }
        return new OrdemServicoResponse.PagamentoView(p.link(), p.expiraEm(), p.confirmadoEm());
    }
}
