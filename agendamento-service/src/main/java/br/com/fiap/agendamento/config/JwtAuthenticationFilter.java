package br.com.fiap.agendamento.config;

import br.com.fiap.agendamento.service.TokenService;
import br.com.fiap.agendamento.service.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Le o header {@code Authorization: Bearer <token>} e popula o SecurityContext.
 *
 * <p>Nao rejeita requisicoes sem token: apenas segue sem autenticar. Quem decide se
 * o recurso exige autenticacao e a SecurityFilterChain / o @PreAuthorize.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIXO = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        extrairToken(request)
                .flatMap(tokenService::validar)
                .ifPresent(usuario -> autenticar(usuario, request));

        filterChain.doFilter(request, response);
    }

    private Optional<String> extrairToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIXO)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(PREFIXO.length()).trim());
    }

    private void autenticar(UsuarioAutenticado usuario, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(
                usuario, null, usuario.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
