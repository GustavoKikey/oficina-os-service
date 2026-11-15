package br.com.fiap.techchallenge.oficina.os.external.config;

import br.com.fiap.techchallenge.oficina.os.relatorio.controllers.RelatorioController;
import br.com.fiap.techchallenge.oficina.os.relatorio.gateways.RelatorioTempoMedioGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Composition root do BC Relatório. */
@ApplicationScoped
public class RelatorioBeans {

    @Produces
    @ApplicationScoped
    RelatorioController relatorioController(RelatorioTempoMedioGateway gateway) {
        return new RelatorioController(gateway);
    }
}
