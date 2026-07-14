package br.com.fiap.notificacao.messaging;

import br.com.fiap.notificacao.config.RabbitConfig;
import br.com.fiap.notificacao.dto.ConsultaEvento;
import br.com.fiap.notificacao.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumidor de {@code notificacoes.queue} (binding {@code consulta.*}).
 *
 * <p>Mensagens que este servico nao consegue processar sao rejeitadas sem reenfileirar
 * e vao para a {@code notificacoes.dlq} — ver {@code EventoInvalidoException}.
 */
@Component
public class ConsultaEventListener {

    private static final Logger log = LoggerFactory.getLogger(ConsultaEventListener.class);

    private final NotificacaoService notificacaoService;

    public ConsultaEventListener(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @RabbitListener(queues = RabbitConfig.FILA)
    public void aoReceberEvento(ConsultaEvento evento,
                                @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {

        log.info("Evento recebido: routingKey={} tipo={} consultaId={} status={}",
                routingKey, evento.tipoEvento(), evento.consultaId(), evento.status());

        notificacaoService.processarEvento(evento);
    }
}
