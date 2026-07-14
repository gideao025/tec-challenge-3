package br.com.fiap.notificacao.service;

import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.dto.StatusConsulta;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LembreteScheduler")
class LembreteSchedulerTest {

    @Mock
    private ConsultaAgendadaRepository consultaRepository;

    @Mock
    private NotificacaoService notificacaoService;

    @InjectMocks
    private LembreteScheduler scheduler;

    @Test
    @DisplayName("busca apenas consultas AGENDADAS dentro da janela de 24h")
    void buscaSomenteAgendadasNaJanelaDe24h() {
        when(consultaRepository.findByStatusAndDataHoraBetweenOrderByDataHoraAsc(
                any(), any(), any())).thenReturn(List.of());

        scheduler.enviarLembretesDasProximas24h();

        ArgumentCaptor<LocalDateTime> inicio = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fim = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(consultaRepository).findByStatusAndDataHoraBetweenOrderByDataHoraAsc(
                eq(StatusConsulta.AGENDADA), inicio.capture(), fim.capture());

        // a janela e de exatamente 24h a partir de agora
        Duration janela = Duration.between(inicio.getValue(), fim.getValue());
        assertThat(janela.toHours()).isEqualTo(24);
    }

    @Test
    @DisplayName("envia um lembrete para cada consulta da janela")
    void enviaLembreteParaCadaConsulta() {
        List<ConsultaAgendada> consultas = List.of(
                consulta(1L, "joao@hospital.com"),
                consulta(2L, "maria@hospital.com"));

        when(consultaRepository.findByStatusAndDataHoraBetweenOrderByDataHoraAsc(any(), any(), any()))
                .thenReturn(consultas);
        when(notificacaoService.enviarLembrete(any(ConsultaAgendada.class))).thenReturn(true);

        scheduler.enviarLembretesDasProximas24h();

        verify(notificacaoService, times(2)).enviarLembrete(any(ConsultaAgendada.class));
    }

    @Test
    @DisplayName("falha em um paciente nao interrompe os lembretes dos demais")
    void falhaEmUmNaoInterrompeOLote() {
        ConsultaAgendada problematica = consulta(1L, "joao@hospital.com");
        ConsultaAgendada saudavel = consulta(2L, "maria@hospital.com");

        when(consultaRepository.findByStatusAndDataHoraBetweenOrderByDataHoraAsc(any(), any(), any()))
                .thenReturn(List.of(problematica, saudavel));
        when(notificacaoService.enviarLembrete(problematica))
                .thenThrow(new RuntimeException("banco indisponivel"));
        when(notificacaoService.enviarLembrete(saudavel)).thenReturn(true);

        scheduler.enviarLembretesDasProximas24h();

        // a segunda consulta ainda e notificada
        verify(notificacaoService).enviarLembrete(saudavel);
    }

    private static ConsultaAgendada consulta(Long id, String email) {
        return new ConsultaAgendada(id, "Paciente " + id, email, "Dr. Carlos Andrade",
                LocalDateTime.now().plusHours(12), StatusConsulta.AGENDADA);
    }
}
