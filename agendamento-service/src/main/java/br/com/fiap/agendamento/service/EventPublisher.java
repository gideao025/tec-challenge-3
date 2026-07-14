package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.dto.ConsultaEvento;

/**
 * Porta de saida para publicacao de eventos de dominio.
 *
 * <p>O {@link ConsultaService} depende desta abstracao, e nao de AMQP: trocar o
 * RabbitMQ por Kafka significa escrever outra implementacao, sem tocar no dominio
 * (e os testes unitarios do service usam um mock desta interface).
 */
public interface EventPublisher {

    void publicar(ConsultaEvento evento);
}
