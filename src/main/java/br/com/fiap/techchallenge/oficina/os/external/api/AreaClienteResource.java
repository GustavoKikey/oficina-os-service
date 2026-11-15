package br.com.fiap.techchallenge.oficina.os.external.api;

import br.com.fiap.techchallenge.oficina.os.atendimento.controllers.OrdemServicoController;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.DecisaoOrcamentoRequest;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.OrdemServicoResponse;
import br.com.fiap.techchallenge.oficina.os.atendimento.dtos.StatusOSResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * Área do cliente autenticado por CPF (fase 3; as rotas abertas {@code /publico/*}
 * foram removidas na fase 4).
 *
 * <p>O portador precisa de um JWT emitido pela Function Serverless de autenticação, e
 * cada operação só alcança <b>as OS do próprio cliente</b>. Na fase 4 a decisão sobre o
 * orçamento é um passo da saga: aprovar dispara a cobrança no Billing.
 *
 * <p>O id do cliente vem do claim {@code sub} do token — nunca de parâmetro da
 * requisição. Aceitar o id do cliente pela URL devolveria ao chamador a chance de
 * escolher de quem são os dados que quer ver, que é exatamente o furo que esta
 * classe existe para fechar. Contrato completo do token: ADR 001.
 */
@Path("/cliente/ordens-servico")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("CLIENTE")
@Tag(name = "Área do cliente", description = "Consulta e decisão de orçamento pelo próprio cliente (autenticação por CPF)")
public class AreaClienteResource {

    private final OrdemServicoController controller;
    private final JsonWebToken jwt;

    public AreaClienteResource(OrdemServicoController controller, JsonWebToken jwt) {
        this.controller = controller;
        this.jwt = jwt;
    }

    @GET
    @Operation(summary = "Lista as ordens de serviço do cliente autenticado")
    @APIResponse(responseCode = "200", description = "Lista das OS do cliente")
    @APIResponse(responseCode = "401", description = "Token ausente ou inválido")
    @APIResponse(responseCode = "403", description = "Token sem a role CLIENTE")
    public List<OrdemServicoResponse> listar() {
        return controller.listarDoCliente(clienteAutenticado());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Detalha uma OS do próprio cliente")
    @APIResponse(responseCode = "200", description = "OS encontrada")
    @APIResponse(responseCode = "403", description = "A OS pertence a outro cliente")
    @APIResponse(responseCode = "404", description = "OS não encontrada")
    public OrdemServicoResponse buscar(@PathParam("id") UUID id) {
        return controller.consultarDoCliente(id, clienteAutenticado());
    }

    @GET
    @Path("/{id}/status")
    @Operation(summary = "Consulta o status de uma OS do próprio cliente")
    @APIResponse(responseCode = "200", description = "Status atual")
    @APIResponse(responseCode = "403", description = "A OS pertence a outro cliente")
    @APIResponse(responseCode = "404", description = "OS não encontrada")
    public StatusOSResponse consultarStatus(@PathParam("id") UUID id) {
        return controller.consultarStatusDoCliente(id, clienteAutenticado());
    }

    @POST
    @Path("/{id}/orcamento/decisao")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Aprova ou recusa o orçamento da própria OS",
            description = "Aprovar gera a cobrança no Mercado Pago: o link aparece em linkPagamento "
                    + "da consulta de status assim que o Billing o criar.")
    @APIResponse(responseCode = "200", description = "Decisão registrada")
    @APIResponse(responseCode = "403", description = "A OS pertence a outro cliente")
    @APIResponse(responseCode = "404", description = "OS não encontrada")
    @APIResponse(responseCode = "409", description = "Orçamento não está aguardando aprovação")
    public StatusOSResponse decidirOrcamento(@PathParam("id") UUID id,
                                             @Valid DecisaoOrcamentoRequest req) {
        return controller.decidirOrcamentoDoCliente(id, clienteAutenticado(), req.aprovado());
    }

    private UUID clienteAutenticado() {
        String sub = jwt.getSubject();
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException(
                    "Token de cliente inválido: 'sub' deve ser o UUID do cliente (ver ADR 001).");
        }
    }
}
