package br.com.fiap.notificacao.service;

import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.dto.StatusConsulta;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agendador dos lembretes automaticos.
 *
 * <p>Roda diariamente e varre a projecao local em busca de consultas AGENDADAS nas
 * proximas 24h. Consultas CANCELADAS ou ja REALIZADAS ficam de fora pelo proprio filtro
 * da consulta — e por isso o evento precisa carregar o status.
 */
@Component
public class LembreteScheduler {

    private static final Logger log = LoggerFactory.getLogger(LembreteScheduler.class);

    private static final int JANELA_HORAS = 24;

    private final ConsultaAgendadaRepository consultaRepository;
    private final NotificacaoService notificacaoService;

    public LembreteScheduler(ConsultaAgendadaRepository consultaRepository,
                             NotificacaoService notificacaoService) {
        this.consultaRepository = consultaRepository;
        this.notificacaoService = notificacaoService;
    }

    /**
     * Disparo diario (cron configuravel em {@code app.notificacao.cron-lembretes}).
     *
     * <p>Nao e {@code @Transactional}: cada envio abre a sua propria transacao no
     * {@link NotificacaoService}, para que a falha em um paciente nao derrube o lote inteiro.
     */
    @Scheduled(cron = "${app.notificacao.cron-lembretes}", zone = "America/Sao_Paulo")
    public void enviarLembretesDasProximas24h() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limite = agora.plusHours(JANELA_HORAS);

        List<ConsultaAgendada> consultas = consultaRepository
                .findByStatusAndDataHoraBetweenOrderByDataHoraAsc(StatusConsulta.AGENDADA, agora, limite);

        if (consultas.isEmpty()) {
            log.info("Nenhuma consulta nas proximas {}h; nenhum lembrete a enviar.", JANELA_HORAS);
            return;
        }

        log.info("Iniciando envio de lembretes: {} consulta(s) entre {} e {}",
                consultas.size(), agora, limite);

        int enviados = 0;
        int ignorados = 0;
        int falhas = 0;

        for (ConsultaAgendada consulta : consultas) {
            try {
                if (notificacaoService.enviarLembrete(consulta)) {
                    enviados++;
                } else {
                    ignorados++;
                }
            } catch (RuntimeException e) {
                falhas++;
                log.error("Erro inesperado ao lembrar consultaId={}: {}",
                        consulta.getId(), e.getMessage(), e);
            }
        }

        log.info("Lembretes concluidos: enviados={} ja_enviados={} falhas={}",
                enviados, ignorados, falhas);
    }
}
