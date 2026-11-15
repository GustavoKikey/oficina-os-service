package br.com.fiap.techchallenge.oficina.os.external.api;

import br.com.fiap.techchallenge.oficina.os.saga.controllers.SagaController;
import br.com.fiap.techchallenge.oficina.os.saga.dtos.SagaResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** Operação da saga (saga.md §8). */
@Path("/admin/sagas")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMINISTRADOR")
@Tag(name = "Saga (operação)", description = "Estado e reprocessamento da saga de cada OS")
public class SagaAdminResource {

    private final SagaController controller;

    public SagaAdminResource(SagaController controller) {
        this.controller = controller;
    }

    @GET
    @Path("/{osId}")
    @Operation(summary = "Estado da saga, prazo, comandos pendentes e mensagens trocadas com os participantes")
    @APIResponse(responseCode = "404", description = "OS não encontrada")
    public SagaResponse consultar(@PathParam("osId") UUID osId) {
        return controller.consultar(osId);
    }

    @POST
    @Path("/{osId}/reprocessar")
    @Operation(summary = "Reenvia o comando do passo em que uma saga em FALHA parou")
    @APIResponse(responseCode = "409", description = "A saga não está em FALHA")
    public SagaResponse reprocessar(@PathParam("osId") UUID osId) {
        return controller.reprocessar(osId);
    }
}
