package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.config.JwtProperties;
import br.com.fiap.agendamento.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emissao e validacao dos tokens JWT (HS256).
 *
 * <p>O token carrega a role como claim para que o filtro monte as authorities sem
 * ir ao banco a cada requisicao — mantendo a autenticacao realmente stateless.
 */
@Service
public class TokenService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USUARIO_ID = "usuarioId";

    private final SecretKey chave;
    private final Duration expiracao;
    private final String emissor;

    public TokenService(JwtProperties propriedades) {
        this.chave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(propriedades.secret()));
        this.expiracao = Duration.ofMinutes(propriedades.expiracaoMinutos());
        this.emissor = propriedades.emissor();
    }

    public String gerar(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim(CLAIM_ROLE, usuario.getRole().name())
                .claim(CLAIM_USUARIO_ID, usuario.getId())
                .issuer(emissor)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracao)))
                .signWith(chave)
                .compact();
    }

    public long expiracaoEmSegundos() {
        return expiracao.toSeconds();
    }

    /**
     * Valida assinatura e expiracao. Retorna vazio para qualquer token invalido —
     * o filtro trata a ausencia de principal como "nao autenticado".
     */
    public Optional<UsuarioAutenticado> validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(emissor)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long usuarioId = claims.get(CLAIM_USUARIO_ID, Number.class).longValue();
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));

            return Optional.of(new UsuarioAutenticado(usuarioId, claims.getSubject(), null, role));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
