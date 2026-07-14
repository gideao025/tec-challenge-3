package br.com.fiap.notificacao.exception;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Payload que este servico nunca conseguira processar (poison message).
 *
 * <p>Estende {@link AmqpRejectAndDontRequeueException} de proposito: reprocessar nao
 * vai consertar um evento malformado, entao ele deve ir direto para a
 * {@code notificacoes.dlq} em vez de bloquear a fila em retentativas.
 */
public class EventoInvalidoException extends AmqpRejectAndDontRequeueException {

    public EventoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
