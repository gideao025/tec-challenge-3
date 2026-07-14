package br.com.fiap.notificacao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio via SMTP. Em desenvolvimento o destino e o Mailhog (porta 1025), cuja caixa de
 * entrada pode ser inspecionada em http://localhost:8025 — nenhum e-mail sai de verdade.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final String remetente;

    public SmtpEmailSender(JavaMailSender javaMailSender,
                           @Value("${app.notificacao.remetente}") String remetente) {
        this.javaMailSender = javaMailSender;
        this.remetente = remetente;
    }

    @Override
    public void enviar(String destinatario, String assunto, String corpo) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(destinatario);
        mensagem.setSubject(assunto);
        mensagem.setText(corpo);

        javaMailSender.send(mensagem);
    }
}
