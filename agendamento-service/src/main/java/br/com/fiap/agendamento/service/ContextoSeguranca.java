package br.com.fiap.agendamento.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acesso ao usuario autenticado.
 *
 * <p>Existe para que os services apliquem a regra de ownership sem depender
 * estaticamente do {@link SecurityContextHolder} — o que permite testa-los com um
 * mock desta classe, sem levantar contexto de seguranca.
 */
@Component
public class ContextoSeguranca {

    public UsuarioAutenticado usuarioAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new IllegalStateException("Nenhum usuario autenticado no contexto");
        }
        return usuario;
    }
}
