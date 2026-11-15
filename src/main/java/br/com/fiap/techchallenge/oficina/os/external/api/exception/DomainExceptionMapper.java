package br.com.fiap.techchallenge.oficina.os.external.api.exception;

import br.com.fiap.techchallenge.oficina.os.atendimento.entities.AcessoNegadoAOrdemServicoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteJaCadastradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.ClienteNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.OrdemServicoNaoEncontradaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.TransicaoStatusInvalidaException;
import br.com.fiap.techchallenge.oficina.os.saga.entities.TransicaoSagaInvalidaException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoJaCadastradoException;
import br.com.fiap.techchallenge.oficina.os.atendimento.entities.VeiculoNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.seguranca.entities.CredenciaisInvalidasException;
import br.com.fiap.techchallenge.oficina.os.seguranca.entities.UsernameJaCadastradoException;
import br.com.fiap.techchallenge.oficina.os.seguranca.entities.UsuarioNaoEncontradoException;
import br.com.fiap.techchallenge.oficina.os.shared.entities.DocumentoInvalidoException;
import br.com.fiap.techchallenge.oficina.os.shared.entities.DomainException;
import br.com.fiap.techchallenge.oficina.os.shared.entities.PlacaInvalidaException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(DomainException ex) {
        int status = mapearStatus(ex);
        String reason = reasonPhrase(status);
        ApiError body = ApiError.of(status, reason, ex.getMessage());
        return Response.status(status).entity(body).build();
    }

    private int mapearStatus(DomainException ex) {
        if (ex instanceof DocumentoInvalidoException
                || ex instanceof PlacaInvalidaException) {
            return Response.Status.BAD_REQUEST.getStatusCode();
        }
        if (ex instanceof CredenciaisInvalidasException) {
            return Response.Status.UNAUTHORIZED.getStatusCode();
        }
        if (ex instanceof ClienteJaCadastradoException
                || ex instanceof VeiculoJaCadastradoException
                || ex instanceof TransicaoStatusInvalidaException
                || ex instanceof TransicaoSagaInvalidaException
                || ex instanceof UsernameJaCadastradoException) {
            return Response.Status.CONFLICT.getStatusCode();
        }
        if (ex instanceof AcessoNegadoAOrdemServicoException) {
            return Response.Status.FORBIDDEN.getStatusCode();
        }
        if (ex instanceof ClienteNaoEncontradoException
                || ex instanceof VeiculoNaoEncontradoException
                || ex instanceof OrdemServicoNaoEncontradaException
                || ex instanceof UsuarioNaoEncontradoException) {
            return Response.Status.NOT_FOUND.getStatusCode();
        }
        return Response.Status.BAD_REQUEST.getStatusCode();
    }

    private String reasonPhrase(int status) {
        Response.Status s = Response.Status.fromStatusCode(status);
        if (s != null) return s.getReasonPhrase();
        if (status == UNPROCESSABLE_ENTITY) return "Unprocessable Entity";
        return "Error";
    }
}
