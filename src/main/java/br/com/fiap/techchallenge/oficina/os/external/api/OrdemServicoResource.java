package br.com.fiap.techchallenge.oficina.os.external.api;

import br.com.fiap.techchallenge.oficina.os.atendimento.controllers.OrdemServicoController;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.AberturaOrdemServicoResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.AbrirOrdemServicoRequest;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.HistoricoOSResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.OrdemServicoResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.StatusOSResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * Ordens de serviço — visão dos funcionários.
 *
 * <p>Na fase 4 diagnóstico, itens e reparo saíram daqui: o mecânico trabalha no
 * Execução Service. Este recurso abre a OS, consulta, registra a decisão do orçamento
 * em nome do cliente e entrega o veículo; o resto do ciclo anda pela saga.
 */
@Path("/ordens-servico")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Ordens de Serviço", description = "Abertura, consulta, histórico, decisão do orçamento e entrega")
public class OrdemServicoResource {

    private final OrdemServicoController controller;

    public OrdemServicoResource(OrdemServicoController controller) {
        this.controller = controller;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"ATENDENTE", "ADMINISTRADOR"})
    @Operation(summary = "Abre a OS e inicia a saga (cliente e veículo novos são cadastrados)",
            description = "A OS nasce RECEBIDA e é enviada à fila de diagnóstico do Execução Service.")
    @APIResponse(responseCode = "201", description = "OS criada (status RECEBIDA)")
    @APIResponse(responseCode = "400", description = "Documento ou placa inválidos")
    @APIResponse(responseCode = "404", description = "Cliente ou veículo inexistente sem dados para cadastrar")
    public Response abrir(@Valid AbrirOrdemServicoRequest req) {
        AberturaOrdemServicoResponse resp = controller.abrir(req);
        return Response.created(UriBuilder.fromResource(OrdemServicoResource.class).path("{id}").build(resp.id()))
                .entity(resp)
                .build();
    }

    @GET
    @RolesAllowed({"ATENDENTE", "MECANICO", "ADMINISTRADOR"})
    @Operation(summary = "Lista OS com filtros opcionais (sem filtro de status: só as em andamento, por prioridade)")
    public List<OrdemServicoResponse> listar(@QueryParam("status") String status,
                                             @QueryParam("clienteId") UUID clienteId,
                                             @QueryParam("veiculoId") UUID veiculoId) {
        return controller.listar(status, clienteId, veiculoId);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ATENDENTE", "MECANICO", "ADMINISTRADOR"})
    @Operation(summary = "Detalhe da OS: itens do diagnóstico, resumo do orçamento, pagamento e datas")
    @APIResponse(responseCode = "404", description = "OS não encontrada")
    public OrdemServicoResponse buscar(@PathParam("id") UUID id) {
        return controller.buscar(id);
    }

    @GET
    @Path("/{id}/status")
    @RolesAllowed({"ATENDENTE", "MECANICO", "ADMINISTRADOR"})
    @Operation(summary = "Status atual com descrição amigável")
    @APIResponse(responseCode = "404", description = "OS não encontrada")
    public StatusOSResponse consultarStatus(@PathParam("id") UUID id) {
        return controller.consultarStatus(id);
    }

    @GET
    @Path("/{id}/historico")
    @RolesAllowed({"ATENDENTE", "MECANICO", "ADMINISTRADOR"})
    @Operation(summary = "Histórico de status da OS, do mais antigo ao mais recente")
    @APIResponse(responseCode = "404", description = "OS não encontrada")
    public HistoricoOSResponse historico(@PathParam("id") UUID id) {
        return controller.historico(id);
    }

    @POST
    @Path("/{id}/orcamento/aprovar")
    @RolesAllowed({"ATENDENTE", "ADMINISTRADOR"})
    @Operation(summary = "Aprova o orçamento em nome do cliente — dispara a cobrança no Billing")
    @APIResponse(responseCode = "409", description = "Orçamento não está aguardando aprovação")
    public OrdemServicoResponse aprovarOrcamento(@PathParam("id") UUID id) {
        return controller.decidirOrcamento(id, true);
    }

    @POST
    @Path("/{id}/orcamento/recusar")
    @RolesAllowed({"ATENDENTE", "ADMINISTRADOR"})
    @Operation(summary = "Recusa o orçamento — cancela a OS e dispara as compensações (libera peças, cancela orçamento)")
    @APIResponse(responseCode = "409", description = "Orçamento não está aguardando aprovação")
    public OrdemServicoResponse recusarOrcamento(@PathParam("id") UUID id) {
        return controller.decidirOrcamento(id, false);
    }

    @POST
    @Path("/{id}/entregar")
    @RolesAllowed({"ATENDENTE", "ADMINISTRADOR"})
    @Operation(summary = "Entrega o veículo (FINALIZADA → ENTREGUE)")
    @APIResponse(responseCode = "409", description = "OS não está finalizada")
    public OrdemServicoResponse entregar(@PathParam("id") UUID id) {
        return controller.entregar(id);
    }
}
