package br.com.fiap.agendamento.messaging;

import br.com.fiap.agendamento.config.MensageriaProperties;
import br.com.fiap.agendamento.dto.ConsultaEvento;
import br.com.fiap.agendamento.dto.TipoEvento;
import br.com.fiap.agendamento.service.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter AMQP da porta {@link EventPublisher}. E a unica classe do servico que
 * conhece RabbitMQ — o dominio permanece agnostico ao broker.
 */
@Component
public class RabbitEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MensageriaProperties propriedades;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate, MensageriaProperties propriedades) {
        this.rabbitTemplate = rabbitTemplate;
        this.propriedades = propriedades;
    }

    @Override
    public void publicar(ConsultaEvento evento) {
        String routingKey = routingKeyDe(evento.tipoEvento());

        rabbitTemplate.convertAndSend(propriedades.exchange(), routingKey, evento);

        log.info("Evento publicado: tipo={} consultaId={} exchange={} routingKey={}",
                evento.tipoEvento(), evento.consultaId(), propriedades.exchange(), routingKey);
    }

    private String routingKeyDe(TipoEvento tipoEvento) {
        return switch (tipoEvento) {
            case CONSULTA_CRIADA -> propriedades.routingKeyCriada();
            case CONSULTA_ATUALIZADA -> propriedades.routingKeyAtualizada();
        };
    }
}
