package br.com.fiap.notificacao.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia consumida pelo notificacao-service.
 *
 * <pre>
 *   consultas.exchange (topic)
 *        |  binding: consulta.*   (consulta.criada, consulta.atualizada)
 *        v
 *   notificacoes.queue  --x-dead-letter-exchange--> notificacoes.dlx --> notificacoes.dlq
 * </pre>
 *
 * <p>A fila e a DLQ sao declaradas aqui, no consumidor — quem consome e quem sabe como
 * quer consumir. As declaracoes AMQP sao idempotentes, entao a ordem de subida dos
 * containers nao importa: quem chegar primeiro cria a topologia.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "consultas.exchange";
    public static final String FILA = "notificacoes.queue";
    public static final String PADRAO_ROTEAMENTO = "consulta.*";

    public static final String DLX = "notificacoes.dlx";
    public static final String DLQ = "notificacoes.dlq";

    @Bean
    public TopicExchange consultasExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * Mensagens rejeitadas (ver {@code default-requeue-rejected: false} no application.yml)
     * sao encaminhadas para a DLQ em vez de voltarem para a fila em loop infinito —
     * um payload malformado nao pode travar o consumo dos demais.
     */
    @Bean
    public Queue notificacoesQueue() {
        return QueueBuilder.durable(FILA)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    public Binding notificacoesBinding(Queue notificacoesQueue, TopicExchange consultasExchange) {
        return BindingBuilder.bind(notificacoesQueue).to(consultasExchange).with(PADRAO_ROTEAMENTO);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ);
    }

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
