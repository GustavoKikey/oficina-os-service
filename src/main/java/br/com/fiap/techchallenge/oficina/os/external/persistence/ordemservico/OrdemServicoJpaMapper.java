package br.com.fiap.techchallenge.oficina.os.external.persistence.ordemservico;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ItemOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.MotivoCancelamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServico;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoId;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.Pagamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ResumoOrcamento;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.StatusOS;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.TransicaoStatus;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoId;
import br.com.fiap.techchallenge.oficina.os.shared.entities.Dinheiro;

import java.util.List;
import java.util.UUID;

final class OrdemServicoJpaMapper {

    private OrdemServicoJpaMapper() {}

    static OrdemServicoJpaEntity toEntity(OrdemServico os, OrdemServicoJpaEntity existente) {
        OrdemServicoJpaEntity e = existente != null ? existente : new OrdemServicoJpaEntity();
        e.id = os.id().valor();
        e.clienteId = os.clienteId().valor();
        e.veiculoId = os.veiculoId().valor();
        e.status = os.status().name();
        e.relatoCliente = os.relatoCliente();

        ResumoOrcamento orc = os.orcamento();
        e.orcamentoId = orc == null ? null : orc.orcamentoId();
        e.valorTotal = orc == null ? null : orc.valorTotal().valor();
        e.orcamentoGeradoEm = orc == null ? null : orc.geradoEm();
        e.orcamentoAprovadoEm = orc == null ? null : orc.aprovadoEm();
        e.orcamentoAprovadoPor = orc == null || orc.aprovadoPor() == null ? null : orc.aprovadoPor().name();

        Pagamento pag = os.pagamento();
        e.linkPagamento = pag.link();
        e.pagamentoExpiraEm = pag.expiraEm();
        e.pagamentoConfirmadoEm = pag.confirmadoEm();

        e.motivoCancelamento = os.motivoCancelamento() == null ? null : os.motivoCancelamento().name();
        e.criadaEm = os.criadaEm();
        e.diagnosticoIniciadoEm = os.diagnosticoIniciadoEm();
        e.execucaoIniciadaEm = os.execucaoIniciadaEm();
        e.finalizadaEm = os.finalizadaEm();
        e.entregueEm = os.entregueEm();
        e.canceladaEm = os.canceladaEm();

        // Os itens chegam uma vez (DiagnosticoConcluido) e não mudam depois: só reescreve
        // quando a cópia mudou, para não apagar e reinserir linhas a cada transição.
        if (e.itens.size() != os.itens().size()) {
            e.itens.clear();
            int posicao = 0;
            for (ItemOS item : os.itens()) {
                OsItemJpaEntity it = new OsItemJpaEntity();
                it.id = UUID.randomUUID();
                it.ordemServico = e;
                it.posicao = posicao++;
                it.tipo = item.tipo().name();
                it.referenciaId = item.referenciaId();
                it.descricao = item.descricao();
                it.quantidade = item.quantidade();
                it.valorUnitario = item.valorUnitario().valor();
                e.itens.add(it);
            }
        }
        return e;
    }

    static OrdemServico toDomain(OrdemServicoJpaEntity e) {
        OrdemServico.Dados d = new OrdemServico.Dados();
        d.id = OrdemServicoId.de(e.id);
        d.clienteId = ClienteId.de(e.clienteId);
        d.veiculoId = VeiculoId.de(e.veiculoId);
        d.relatoCliente = e.relatoCliente;
        d.status = StatusOS.valueOf(e.status);
        d.itens = e.itens.stream().map(OrdemServicoJpaMapper::toDomain).toList();
        if (e.orcamentoId != null) {
            d.orcamento = new ResumoOrcamento(e.orcamentoId, Dinheiro.de(e.valorTotal), e.orcamentoGeradoEm,
                    e.orcamentoAprovadoEm,
                    e.orcamentoAprovadoPor == null ? null : ResumoOrcamento.AprovadoPor.valueOf(e.orcamentoAprovadoPor));
        }
        d.pagamento = new Pagamento(e.linkPagamento, e.pagamentoExpiraEm, e.pagamentoConfirmadoEm);
        d.motivoCancelamento = e.motivoCancelamento == null ? null : MotivoCancelamento.valueOf(e.motivoCancelamento);
        d.criadaEm = e.criadaEm;
        d.diagnosticoIniciadoEm = e.diagnosticoIniciadoEm;
        d.execucaoIniciadaEm = e.execucaoIniciadaEm;
        d.finalizadaEm = e.finalizadaEm;
        d.entregueEm = e.entregueEm;
        d.canceladaEm = e.canceladaEm;
        return OrdemServico.reconstituir(d);
    }

    private static ItemOS toDomain(OsItemJpaEntity it) {
        return new ItemOS(ItemOS.TipoItem.valueOf(it.tipo), it.referenciaId, it.descricao, it.quantidade,
                Dinheiro.de(it.valorUnitario));
    }

    static OsHistoricoJpaEntity toEntity(OrdemServicoId osId, TransicaoStatus t) {
        OsHistoricoJpaEntity h = new OsHistoricoJpaEntity();
        h.osId = osId.valor();
        h.de = t.de() == null ? null : t.de().name();
        h.para = t.para().name();
        h.em = t.em();
        h.motivo = t.motivo();
        return h;
    }

    static List<TransicaoStatus> toDomain(List<OsHistoricoJpaEntity> historico) {
        return historico.stream()
                .map(h -> new TransicaoStatus(h.de == null ? null : StatusOS.valueOf(h.de), StatusOS.valueOf(h.para),
                        h.em, h.motivo))
                .toList();
    }
}
