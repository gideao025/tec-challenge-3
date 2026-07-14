package br.com.fiap.agendamento.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do token JWT (prefixo {@code app.jwt}).
 *
 * @param secret            chave HS256 em Base64 (minimo 32 bytes apos decodificar)
 * @param expiracaoMinutos  validade do token em minutos
 * @param emissor           claim {@code iss}
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expiracaoMinutos, String emissor) {
}
