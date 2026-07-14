package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.dto.LoginRequest;
import br.com.fiap.agendamento.dto.LoginResponse;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacaoService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    public AutenticacaoService(AuthenticationManager authenticationManager,
                               UsuarioRepository usuarioRepository,
                               TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
    }

    /**
     * Valida as credenciais (BCrypt, via AuthenticationManager) e emite o JWT.
     *
     * @throws BadCredentialsException se e-mail ou senha nao conferirem
     */
    @Transactional(readOnly = true)
    public LoginResponse autenticar(LoginRequest requisicao) {
        Authentication autenticacao = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(requisicao.email(), requisicao.senha()));

        UsuarioAutenticado principal = (UsuarioAutenticado) autenticacao.getPrincipal();

        Usuario usuario = usuarioRepository.findByEmail(principal.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciais invalidas"));

        String token = tokenService.gerar(principal);

        return LoginResponse.bearer(
                token,
                tokenService.expiracaoEmSegundos(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getRole());
    }
}
