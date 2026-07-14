package br.com.fiap.notificacao.service;

import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.domain.Notificacao;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import br.com.fiap.notificacao.dto.ConsultaEvento;
import br.com.fiap.notificacao.dto.StatusConsulta;
import br.com.fiap.notificacao.exception.EventoInvalidoException;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import br.com.fiap.notificacao.repository.NotificacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Processa os eventos de consulta e dispara os lembretes ao paciente.
 *
 * <p>Cada evento faz duas coisas: atualiza a projecao local (que o agendador consulta
 * depois) e envia o aviso correspondente ao paciente.
 */
@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private static final DateTimeFormatter FORMATO_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    private final ConsultaAgendadaRepository consultaRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final EmailSender emailSender;

    public NotificacaoService(ConsultaAgendadaRepository consultaRepository,
                              NotificacaoRepository notificacaoRepository,
                              EmailSender emailSender) {
        this.consultaRepository = consultaRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.emailSender = emailSender;
    }

    /**
     * Trata um evento vindo do agendamento-service.
     *
     * @throws EventoInvalidoException se o payload nao tiver o minimo necessario —
     *                                 o listener converte isso em descarte para a DLQ
     */
    @Transactional
    public void processarEvento(ConsultaEvento evento) {
        validar(evento);

        LocalDateTime dataHora = parsearDataHora(evento.dataHora(), evento.consultaId());

        atualizarProjecao(evento, dataHora);

        TipoNotificacao tipo = switch (evento.tipoEvento()) {
            case CONSULTA_CRIADA -> TipoNotificacao.CONFIRMACAO;
            case CONSULTA_ATUALIZADA -> TipoNotificacao.ATUALIZACAO;
        };

        enviar(tipo,
                evento.consultaId(),
                evento.pacienteEmail(),
                assuntoDe(tipo, evento.status()),
                corpoDe(tipo, evento.pacienteNome(), evento.medicoNome(), dataHora, evento.status()),
                dataHora);
    }

    /** Lembrete das consultas nas proximas 24h, disparado pelo agendador. */
    @Transactional
    public boolean enviarLembrete(ConsultaAgendada consulta) {
        boolean jaEnviado = notificacaoRepository
                .existsByConsultaIdAndTipoAndDataHoraConsultaAndStatus(
                        consulta.getId(), TipoNotificacao.LEMBRETE_24H, consulta.getDataHora(),
                        Notificacao.StatusEnvio.ENVIADA);

        if (jaEnviado) {
            log.debug("Lembrete ja enviado; ignorando. consultaId={} dataHora={}",
                    consulta.getId(), consulta.getDataHora());
            return false;
        }

        return enviar(TipoNotificacao.LEMBRETE_24H,
                consulta.getId(),
                consulta.getPacienteEmail(),
                "Lembrete: sua consulta e amanha",
                corpoDe(TipoNotificacao.LEMBRETE_24H, consulta.getPacienteNome(),
                        consulta.getMedicoNome(), consulta.getDataHora(), consulta.getStatus()),
                consulta.getDataHora());
    }

    /**
     * Envia e registra o resultado.
     *
     * <p>Uma falha de SMTP e registrada como FALHA e <b>nao</b> propaga: a projecao local
     * (que ja foi atualizada) e um estado valioso, e derrubar a transacao por causa do
     * e-mail faria a mensagem cair na DLQ e o servico perder a consulta de vista. O
     * lembrete diario tem nova chance de alcancar o paciente.
     */
    private boolean enviar(TipoNotificacao tipo, Long consultaId, String destinatario,
                           String assunto, String corpo, LocalDateTime dataHoraConsulta) {
        try {
            emailSender.enviar(destinatario, assunto, corpo);

            notificacaoRepository.save(Notificacao.enviada(
                    consultaId, tipo, destinatario, assunto, corpo, dataHoraConsulta));

            log.info("Notificacao enviada: tipo={} consultaId={} destinatario={} dataHoraConsulta={}",
                    tipo, consultaId, destinatario, dataHoraConsulta);
            return true;

        } catch (MailException e) {
            notificacaoRepository.save(Notificacao.falha(
                    consultaId, tipo, destinatario, assunto, corpo, dataHoraConsulta, e.getMessage()));

            log.error("Falha ao enviar notificacao: tipo={} consultaId={} destinatario={} erro={}",
                    tipo, consultaId, destinatario, e.getMessage());
            return false;
        }
    }

    /** Upsert da projecao: o id da consulta e a chave, entao reprocessar e inofensivo. */
    private void atualizarProjecao(ConsultaEvento evento, LocalDateTime dataHora) {
        consultaRepository.findById(evento.consultaId())
                .ifPresentOrElse(
                        consulta -> consulta.atualizar(evento.pacienteNome(), evento.pacienteEmail(),
                                evento.medicoNome(), dataHora, evento.status()),
                        () -> consultaRepository.save(new ConsultaAgendada(
                                evento.consultaId(), evento.pacienteNome(), evento.pacienteEmail(),
                                evento.medicoNome(), dataHora, evento.status())));
    }

    private String assuntoDe(TipoNotificacao tipo, StatusConsulta status) {
        if (status == StatusConsulta.CANCELADA) {
            return "Sua consulta foi cancelada";
        }
        return tipo == TipoNotificacao.CONFIRMACAO
                ? "Consulta agendada com sucesso"
                : "Sua consulta foi atualizada";
    }

    private String corpoDe(TipoNotificacao tipo, String pacienteNome, String medicoNome,
                           LocalDateTime dataHora, StatusConsulta status) {
        String saudacao = "Ola, %s!".formatted(pacienteNome);
        String quando = dataHora.format(FORMATO_BR);

        String miolo = switch (status) {
            case CANCELADA -> "Sua consulta com %s, marcada para %s, foi CANCELADA."
                    .formatted(medicoNome, quando);
            case REALIZADA -> "Sua consulta com %s em %s foi registrada como REALIZADA."
                    .formatted(medicoNome, quando);
            case AGENDADA -> switch (tipo) {
                case CONFIRMACAO -> "Sua consulta com %s foi agendada para %s."
                        .formatted(medicoNome, quando);
                case ATUALIZACAO -> "Sua consulta com %s foi atualizada. Novo horario: %s."
                        .formatted(medicoNome, quando);
                case LEMBRETE_24H -> "Lembrete: voce tem uma consulta com %s em %s. Nao se esqueca!"
                        .formatted(medicoNome, quando);
            };
        };

        return "%s%n%n%s%n%n-- Hospital FIAP".formatted(saudacao, miolo);
    }

    private void validar(ConsultaEvento evento) {
        if (evento == null || evento.consultaId() == null) {
            throw new EventoInvalidoException("Evento sem consultaId");
        }
        if (evento.tipoEvento() == null) {
            throw new EventoInvalidoException(
                    "Evento sem tipoEvento (consultaId=%d)".formatted(evento.consultaId()));
        }
        if (evento.status() == null) {
            throw new EventoInvalidoException(
                    "Evento sem status (consultaId=%d)".formatted(evento.consultaId()));
        }
        if (evento.pacienteEmail() == null || evento.pacienteEmail().isBlank()) {
            throw new EventoInvalidoException(
                    "Evento sem pacienteEmail (consultaId=%d)".formatted(evento.consultaId()));
        }
    }

    private LocalDateTime parsearDataHora(String valor, Long consultaId) {
        try {
            return LocalDateTime.parse(valor);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new EventoInvalidoException(
                    "dataHora invalida '%s' (consultaId=%d)".formatted(valor, consultaId));
        }
    }
}
