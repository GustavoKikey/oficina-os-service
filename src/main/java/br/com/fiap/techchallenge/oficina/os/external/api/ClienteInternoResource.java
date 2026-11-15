package br.com.fiap.techchallenge.oficina.os.external.api;

import br.com.fiap.techchallenge.oficina.os.atendimento.controllers.ClienteController;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.ClienteAutenticacaoResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Consulta interna de cliente pela Lambda de autenticação (ADR 011): substitui a leitura
 * direta da tabela {@code clientes}, que feria "nenhum serviço acessa o banco de outro".
 *
 * <p>Fora do Swagger público e protegido por segredo compartilhado
 * ({@code X-Internal-Token}). No Kubernetes, a rota só é exposta por um load balancer
 * interno à VPC — o token é defesa em profundidade, não a única barreira.
 */
@Path("/internal/clientes")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class ClienteInternoResource {

    private final ClienteController clientes;
    private final Optional<String> tokenEsperado;

    public ClienteInternoResource(ClienteController clientes,
                                  @ConfigProperty(name = "oficina.interno.token") Optional<String> tokenEsperado) {
        this.clientes = clientes;
        this.tokenEsperado = tokenEsperado.filter(t -> !t.isBlank());
    }

    @GET
    @Operation(hidden = true)
    public ClienteAutenticacaoResponse buscarPorDocumento(@QueryParam("documento") String documento,
                                                     @HeaderParam("X-Internal-Token") String token) {
        if (tokenEsperado.isEmpty() || token == null || !iguais(tokenEsperado.get(), token)) {
            // Sem token configurado a rota fica fechada: falha segura, nunca aberta.
            throw new ForbiddenException();
        }
        if (documento == null || documento.isBlank()) {
            throw new NotFoundException();
        }
        return clientes.buscarParaAutenticacao(documento).orElseThrow(NotFoundException::new);
    }

    /** Comparação em tempo constante — não vaza, pelo tempo de resposta, quantos caracteres batem. */
    private static boolean iguais(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
