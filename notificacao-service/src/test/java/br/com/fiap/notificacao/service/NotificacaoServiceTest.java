package br.com.fiap.notificacao.service;

import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.domain.Notificacao;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import br.com.fiap.notificacao.dto.ConsultaEvento;
import br.com.fiap.notificacao.dto.StatusConsulta;
import br.com.fiap.notificacao.dto.TipoEvento;
import br.com.fiap.notificacao.exception.EventoInvalidoException;
import br.com.fiap.notificacao.repository.ConsultaAgendadaRepository;
import br.com.fiap.notificacao.repository.NotificacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacaoService")
class NotificacaoServiceTest {

    @Mock
    private ConsultaAgendadaRepository consultaRepository;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private NotificacaoService notificacaoService;

    @Nested
    @DisplayName("processamento de eventos")
    class Eventos {

        @Test
        @DisplayName("consulta criada: grava a projecao local e envia confirmacao")
        void consultaCriadaEnviaConfirmacao() {
            LocalDateTime dataHora = LocalDateTime.now().plusDays(3).withNano(0);
            when(consultaRepository.findById(1L)).thenReturn(Optional.empty());

            notificacaoService.processarEvento(
                    evento(1L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, dataHora));

            // projecao criada
            ArgumentCaptor<ConsultaAgendada> projecao = ArgumentCaptor.forClass(ConsultaAgendada.class);
            verify(consultaRepository).save(projecao.capture());
            assertThat(projecao.getValue().getId()).isEqualTo(1L);
            assertThat(projecao.getValue().getStatus()).isEqualTo(StatusConsulta.AGENDADA);

            // e-mail enviado
            ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
            verify(emailSender).enviar(eq("paciente@hospital.com"), assunto.capture(), corpo.capture());
            assertThat(assunto.getValue()).isEqualTo("Consulta agendada com sucesso");
            assertThat(corpo.getValue()).contains("Joao Souza").contains("Dr. Carlos Andrade");

            // registro persistido
            ArgumentCaptor<Notificacao> registro = ArgumentCaptor.forClass(Notificacao.class);
            verify(notificacaoRepository).save(registro.capture());
            assertThat(registro.getValue().getTipo()).isEqualTo(TipoNotificacao.CONFIRMACAO);
            assertThat(registro.getValue().getStatus()).isEqualTo(Notificacao.StatusEnvio.ENVIADA);
        }

        @Test
        @DisplayName("reprocessar o mesmo evento apenas sobrescreve a projecao (idempotente)")
        void reprocessarAtualizaProjecaoExistente() {
            LocalDateTime dataHora = LocalDateTime.now().plusDays(3).withNano(0);
            ConsultaAgendada existente = new ConsultaAgendada(1L, "Joao Souza",
                    "paciente@hospital.com", "Dr. Carlos Andrade", dataHora, StatusConsulta.AGENDADA);
            when(consultaRepository.findById(1L)).thenReturn(Optional.of(existente));

            notificacaoService.processarEvento(
                    evento(1L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, dataHora));

            // atualiza a entidade gerenciada; nao insere uma segunda linha
            verify(consultaRepository, never()).save(any(ConsultaAgendada.class));
            assertThat(existente.getStatus()).isEqualTo(StatusConsulta.AGENDADA);
        }

        @Test
        @DisplayName("cancelamento avisa o paciente e marca a projecao como CANCELADA")
        void cancelamentoAvisaPaciente() {
            LocalDateTime dataHora = LocalDateTime.now().plusDays(3).withNano(0);
            ConsultaAgendada existente = new ConsultaAgendada(1L, "Joao Souza",
                    "paciente@hospital.com", "Dr. Carlos Andrade", dataHora, StatusConsulta.AGENDADA);
            when(consultaRepository.findById(1L)).thenReturn(Optional.of(existente));

            notificacaoService.processarEvento(
                    evento(1L, TipoEvento.CONSULTA_ATUALIZADA, StatusConsulta.CANCELADA, dataHora));

            // e o que tira a consulta do radar do agendador de lembretes
            assertThat(existente.getStatus()).isEqualTo(StatusConsulta.CANCELADA);

            ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
            verify(emailSender).enviar(anyString(), assunto.capture(), anyString());
            assertThat(assunto.getValue()).isEqualTo("Sua consulta foi cancelada");
        }

        @Test
        @DisplayName("falha de SMTP e registrada como FALHA, sem derrubar o processamento")
        void falhaDeSmtpERegistrada() {
            LocalDateTime dataHora = LocalDateTime.now().plusDays(3).withNano(0);
            when(consultaRepository.findById(1L)).thenReturn(Optional.empty());
            doThrow(new MailSendException("servidor SMTP indisponivel"))
                    .when(emailSender).enviar(anyString(), anyString(), anyString());

            // nao lanca: a projecao local ja foi atualizada e nao pode ser perdida
            notificacaoService.processarEvento(
                    evento(1L, TipoEvento.CONSULTA_CRIADA, StatusConsulta.AGENDADA, dataHora));

            ArgumentCaptor<Notificacao> registro = ArgumentCaptor.forClass(Notificacao.class);
            verify(notificacaoRepository).save(registro.capture());
            assertThat(registro.getValue().getStatus()).isEqualTo(Notificacao.StatusEnvio.FALHA);
            assertThat(registro.getValue().getErro()).contains("SMTP");
        }

        @Test
        @DisplayName("evento sem status e rejeitado para a DLQ")
        void eventoSemStatusVaiParaDlq() {
            ConsultaEvento invalido = new ConsultaEvento(1L, "Joao", "paciente@hospital.com",
                    LocalDateTime.now().plusDays(1).toString(), "Dr. Carlos", null,
                    TipoEvento.CONSULTA_CRIADA);

            assertThatThrownBy(() -> notificacaoService.processarEvento(invalido))
                    .isInstanceOf(EventoInvalidoException.class)
                    .hasMessageContaining("status");

            verifyNoInteractions(emailSender);
        }

        @Test
        @DisplayName("evento com dataHora invalida e rejeitado para a DLQ")
        void eventoComDataInvalidaVaiParaDlq() {
            ConsultaEvento invalido = new ConsultaEvento(1L, "Joao", "paciente@hospital.com",
                    "ontem as 3", "Dr. Carlos", StatusConsulta.AGENDADA, TipoEvento.CONSULTA_CRIADA);

            assertThatThrownBy(() -> notificacaoService.processarEvento(invalido))
                    .isInstanceOf(EventoInvalidoException.class)
                    .hasMessageContaining("dataHora");

            verifyNoInteractions(emailSender);
        }
    }

    @Nested
    @DisplayName("lembrete de 24h")
    class Lembrete {

        @Test
        @DisplayName("envia o lembrete quando ainda nao houve envio")
        void enviaLembreteInedito() {
            ConsultaAgendada consulta = consultaAgendada(LocalDateTime.now().plusHours(20));
            when(notificacaoRepository.existsByConsultaIdAndTipoAndDataHoraConsultaAndStatus(
                    anyLong(), eq(TipoNotificacao.LEMBRETE_24H), any(LocalDateTime.class),
                    eq(Notificacao.StatusEnvio.ENVIADA)))
                    .thenReturn(false);

            boolean enviado = notificacaoService.enviarLembrete(consulta);

            assertThat(enviado).isTrue();

            ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
            verify(emailSender).enviar(eq("paciente@hospital.com"),
                    eq("Lembrete: sua consulta e amanha"), corpo.capture());
            assertThat(corpo.getValue()).contains("Nao se esqueca");
        }

        @Test
        @DisplayName("nao reenvia lembrete ja enviado para a mesma data (idempotencia)")
        void naoReenviaLembreteJaEnviado() {
            ConsultaAgendada consulta = consultaAgendada(LocalDateTime.now().plusHours(20));
            when(notificacaoRepository.existsByConsultaIdAndTipoAndDataHoraConsultaAndStatus(
                    anyLong(), eq(TipoNotificacao.LEMBRETE_24H), any(LocalDateTime.class),
                    eq(Notificacao.StatusEnvio.ENVIADA)))
                    .thenReturn(true);

            boolean enviado = notificacaoService.enviarLembrete(consulta);

            assertThat(enviado).isFalse();
            verifyNoInteractions(emailSender);
            verify(notificacaoRepository, never()).save(any(Notificacao.class));
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static ConsultaEvento evento(Long id, TipoEvento tipo, StatusConsulta status,
                                         LocalDateTime dataHora) {
        return new ConsultaEvento(
                id,
                "Joao Souza",
                "paciente@hospital.com",
                dataHora.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "Dr. Carlos Andrade",
                status,
                tipo);
    }

    private static ConsultaAgendada consultaAgendada(LocalDateTime dataHora) {
        return new ConsultaAgendada(1L, "Joao Souza", "paciente@hospital.com",
                "Dr. Carlos Andrade", dataHora, StatusConsulta.AGENDADA);
    }
}
