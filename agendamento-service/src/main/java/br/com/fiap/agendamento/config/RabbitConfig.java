package br.com.fiap.agendamento.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topologia de publicacao do agendamento-service.
 *
 * <p>Declara apenas a exchange: a fila e a DLQ pertencem ao consumidor
 * (notificacao-service), que e quem sabe como quer consumir. Como as declaracoes
 * AMQP sao idempotentes, a ordem de subida dos containers nao importa.
 */
@Configuration
@EnableConfigurationProperties(MensageriaProperties.class)
public class RabbitConfig {

    /** Topic (e nao direct/fanout) para permitir novos consumidores via padrao {@code consulta.*}. */
    @Bean
    public TopicExchange consultasExchange(MensageriaProperties propriedades) {
        return new TopicExchange(propriedades.exchange(), true, false);
    }

    /**
     * Serializa o payload como JSON. Reaproveita o ObjectMapper do Boot (que ja traz
     * o modulo de java.time), evitando divergencia de formato entre HTTP e AMQP.
     */
    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
