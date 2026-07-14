package br.com.fiap.agendamento.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topologia de publicacao (prefixo {@code app.mensageria}).
 *
 * @param exchange              exchange topic de consultas
 * @param routingKeyCriada      routing key de consulta criada
 * @param routingKeyAtualizada  routing key de consulta atualizada
 */
@ConfigurationProperties(prefix = "app.mensageria")
public record MensageriaProperties(
        String exchange,
        String routingKeyCriada,
        String routingKeyAtualizada) {
}
