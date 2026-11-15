package br.com.fiap.techchallenge.oficina.os.seguranca.presenters;

import br.com.fiap.techchallenge.oficina.os.seguranca.dtos.LoginResponse;
import br.com.fiap.techchallenge.oficina.os.seguranca.dtos.UsuarioResponse;
import br.com.fiap.techchallenge.oficina.os.seguranca.entities.Usuario;
import br.com.fiap.techchallenge.oficina.os.seguranca.usecases.AutenticarUsuarioUseCase;

/**
 * Presenter do BC Segurança — única responsabilidade: formatar a saída do núcleo
 * em DTOs de resposta. A lógica de montagem saiu dos {@code *Response.from(...)}
 * para cá, deixando os DTOs como records puros (sem comportamento).
 */
public class SegurancaPresenter {

    public UsuarioResponse apresentar(Usuario usuario) {
        return new UsuarioResponse(
                usuario.id().valor(),
                usuario.username(),
                usuario.role().name(),
                usuario.ativo());
    }

    public LoginResponse apresentar(AutenticarUsuarioUseCase.Output out) {
        return new LoginResponse(out.accessToken(), out.expiresIn(), out.role());
    }
}
