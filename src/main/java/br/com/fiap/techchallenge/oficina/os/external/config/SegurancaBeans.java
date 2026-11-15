package br.com.fiap.techchallenge.oficina.os.external.config;

import br.com.fiap.techchallenge.oficina.os.seguranca.controllers.SegurancaController;
import br.com.fiap.techchallenge.oficina.os.seguranca.gateways.PasswordHasher;
import br.com.fiap.techchallenge.oficina.os.seguranca.gateways.TokenService;
import br.com.fiap.techchallenge.oficina.os.seguranca.gateways.UsuarioGateway;
import br.com.fiap.techchallenge.oficina.os.shared.usecases.ExecutorTransacional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Composition root do BC Segurança. Único lugar que conhece CDI <i>e</i> os
 * controllers/use cases ao mesmo tempo: monta o grafo de objetos do núcleo a
 * partir dos gateways (beans da infra). Mantém Controllers e Use Cases como POJOs
 * livres de anotação de framework.
 */
@ApplicationScoped
public class SegurancaBeans {

    @Produces
    @ApplicationScoped
    SegurancaController segurancaController(UsuarioGateway usuarioGateway,
                                            PasswordHasher hasher,
                                            TokenService tokenService,
                                            ExecutorTransacional tx) {
        return new SegurancaController(usuarioGateway, hasher, tokenService, tx);
    }
}
