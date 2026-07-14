package br.com.fiap.notificacao.service;

/**
 * Porta de saida do envio de e-mail. Mantem o {@link NotificacaoService} independente
 * do JavaMail — e permite testa-lo com um mock, sem SMTP no meio.
 */
public interface EmailSender {

    void enviar(String destinatario, String assunto, String corpo);
}
