package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.Medico;
import br.com.fiap.agendamento.domain.Paciente;
import br.com.fiap.agendamento.domain.Role;
import br.com.fiap.agendamento.domain.StatusConsulta;
import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.dto.AtualizarConsultaInput;
import br.com.fiap.agendamento.dto.ConsultaDto;
import br.com.fiap.agendamento.dto.ConsultaEvento;
import br.com.fiap.agendamento.dto.CriarConsultaInput;
import br.com.fiap.agendamento.dto.TipoEvento;
import br.com.fiap.agendamento.exception.RecursoNaoEncontradoException;
import br.com.fiap.agendamento.exception.RegraDeNegocioException;
import br.com.fiap.agendamento.repository.ConsultaRepository;
import br.com.fiap.agendamento.repository.MedicoRepository;
import br.com.fiap.agendamento.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultaService")
class ConsultaServiceTest {

    private static final long ID_PACIENTE_JOAO = 1L;
    private static final long ID_PACIENTE_MARIA = 2L;

    @Mock
    private ConsultaRepository consultaRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private MedicoRepository medicoRepository;

    @Mock
    private ContextoSeguranca contextoSeguranca;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ConsultaService consultaService;

    private Paciente joao;
    private Medico carlos;

    @BeforeEach
    void preparar() {
        joao = paciente(ID_PACIENTE_JOAO, "Joao Souza", "paciente@hospital.com");
        carlos = medico();
    }

    // ---------------------------------------------------------------
    // Ownership: o coracao das regras de autorizacao do desafio
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("regra de ownership do paciente")
    class Ownership {

        @Test
        @DisplayName("paciente sem informar pacienteId recebe o proprio historico")
        void pacienteSemIdRecebeOProprioHistorico() {
            autenticarComo(Role.PACIENTE, "paciente@hospital.com");
            when(pacienteRepository.findByUsuarioEmail("paciente@hospital.com"))
                    .thenReturn(Optional.of(joao));
            when(consultaRepository.findByPacienteIdOrderByDataHoraDesc(ID_PACIENTE_JOAO))
                    .thenReturn(List.of(consulta(10L, joao, LocalDateTime.now().plusDays(1))));

            List<ConsultaDto> consultas = consultaService.buscarPorPaciente(null, false);

            assertThat(consultas).hasSize(1);
            assertThat(consultas.getFirst().paciente().id()).isEqualTo(ID_PACIENTE_JOAO);
        }

        @Test
        @DisplayName("paciente que pede o historico de outro paciente recebe acesso negado")
        void pacienteNaoAcessaHistoricoDeTerceiro() {
            autenticarComo(Role.PACIENTE, "paciente@hospital.com");
            when(pacienteRepository.findByUsuarioEmail("paciente@hospital.com"))
                    .thenReturn(Optional.of(joao));

            assertThatThrownBy(() -> consultaService.buscarPorPaciente(ID_PACIENTE_MARIA, false))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("proprio historico");

            // o filtro nunca chega ao banco com o id de terceiro
            verify(consultaRepository, never()).findByPacienteIdOrderByDataHoraDesc(anyLong());
        }

        @Test
        @DisplayName("paciente informando o proprio id e atendido normalmente")
        void pacienteInformandoOProprioIdEAtendido() {
            autenticarComo(Role.PACIENTE, "paciente@hospital.com");
            when(pacienteRepository.findByUsuarioEmail("paciente@hospital.com"))
                    .thenReturn(Optional.of(joao));
            when(consultaRepository.findByPacienteIdOrderByDataHoraDesc(ID_PACIENTE_JOAO))
                    .thenReturn(List.of());

            assertThat(consultaService.buscarPorPaciente(ID_PACIENTE_JOAO, false)).isEmpty();
        }

        @Test
        @DisplayName("paciente nao acessa consulta de outro paciente pelo id da consulta")
        void pacienteNaoAcessaConsultaDeTerceiroPorId() {
            autenticarComo(Role.PACIENTE, "paciente@hospital.com");
            Paciente maria = paciente(ID_PACIENTE_MARIA, "Maria Lima", "paciente2@hospital.com");
            when(consultaRepository.findWithDetalhesById(99L))
                    .thenReturn(Optional.of(consulta(99L, maria, LocalDateTime.now().plusDays(3))));
            when(pacienteRepository.findByUsuarioEmail("paciente@hospital.com"))
                    .thenReturn(Optional.of(joao));

            assertThatThrownBy(() -> consultaService.buscarPorId(99L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("proprias consultas");
        }

        @Test
        @DisplayName("medico consulta o historico de qualquer paciente")
        void medicoConsultaQualquerPaciente() {
            autenticarComo(Role.MEDICO, "medico@hospital.com");
            when(consultaRepository.findByPacienteIdOrderByDataHoraDesc(ID_PACIENTE_MARIA))
                    .thenReturn(List.of());

            assertThat(consultaService.buscarPorPaciente(ID_PACIENTE_MARIA, false)).isEmpty();

            // nenhum ownership e aplicado: o repositorio de paciente sequer e tocado
            verifyNoInteractions(pacienteRepository);
        }

        @Test
        @DisplayName("medico e obrigado a informar o pacienteId")
        void medicoPrecisaInformarPacienteId() {
            autenticarComo(Role.MEDICO, "medico@hospital.com");

            assertThatThrownBy(() -> consultaService.buscarPorPaciente(null, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pacienteId");
        }
    }

    @Nested
    @DisplayName("filtro apenasFuturas")
    class ApenasFuturas {

        @Test
        @DisplayName("apenasFuturas=true consulta somente a agenda a partir de agora")
        void apenasFuturasUsaConsultaComRecorte() {
            autenticarComo(Role.ENFERMEIRO, "enfermeiro@hospital.com");
            when(consultaRepository.findByPacienteIdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
                    eq(ID_PACIENTE_JOAO), any(LocalDateTime.class)))
                    .thenReturn(List.of(consulta(11L, joao, LocalDateTime.now().plusDays(2))));

            List<ConsultaDto> consultas = consultaService.buscarPorPaciente(ID_PACIENTE_JOAO, true);

            assertThat(consultas).hasSize(1);
            verify(consultaRepository, never()).findByPacienteIdOrderByDataHoraDesc(anyLong());
        }
    }

    @Nested
    @DisplayName("criacao de consulta")
    class Criacao {

        @Test
        @DisplayName("cria consulta AGENDADA e publica evento CONSULTA_CRIADA")
        void criaEPublicaEvento() {
            LocalDateTime dataHora = LocalDateTime.now().plusDays(7).withNano(0);

            when(pacienteRepository.findById(ID_PACIENTE_JOAO)).thenReturn(Optional.of(joao));
            when(medicoRepository.findById(1L)).thenReturn(Optional.of(carlos));
            when(consultaRepository.save(any(Consulta.class)))
                    .thenAnswer(invocacao -> {
                        Consulta consulta = invocacao.getArgument(0);
                        ReflectionTestUtils.setField(consulta, "id", 42L);
                        ReflectionTestUtils.setField(consulta, "criadoEm", LocalDateTime.now());
                        ReflectionTestUtils.setField(consulta, "atualizadoEm", LocalDateTime.now());
                        return consulta;
                    });

            ConsultaDto criada = consultaService.criar(new CriarConsultaInput(
                    ID_PACIENTE_JOAO, 1L, dataHora.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), "Retorno"));

            assertThat(criada.id()).isEqualTo(42L);
            assertThat(criada.status()).isEqualTo(StatusConsulta.AGENDADA);

            ArgumentCaptor<ConsultaEvento> capturado = ArgumentCaptor.forClass(ConsultaEvento.class);
            verify(eventPublisher).publicar(capturado.capture());

            ConsultaEvento evento = capturado.getValue();
            assertThat(evento.tipoEvento()).isEqualTo(TipoEvento.CONSULTA_CRIADA);
            assertThat(evento.consultaId()).isEqualTo(42L);
            assertThat(evento.pacienteEmail()).isEqualTo("paciente@hospital.com");
            assertThat(evento.medicoNome()).isEqualTo("Dr. Carlos Andrade");
            assertThat(evento.status()).isEqualTo(StatusConsulta.AGENDADA);
        }

        @Test
        @DisplayName("recusa agendamento no passado e nao publica evento")
        void recusaAgendamentoNoPassado() {
            when(pacienteRepository.findById(ID_PACIENTE_JOAO)).thenReturn(Optional.of(joao));
            when(medicoRepository.findById(1L)).thenReturn(Optional.of(carlos));

            String ontem = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            assertThatThrownBy(() -> consultaService.criar(
                    new CriarConsultaInput(ID_PACIENTE_JOAO, 1L, ontem, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("passado");

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("recusa paciente inexistente")
        void recusaPacienteInexistente() {
            when(pacienteRepository.findById(404L)).thenReturn(Optional.empty());

            String amanha = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            assertThatThrownBy(() -> consultaService.criar(
                    new CriarConsultaInput(404L, 1L, amanha, null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("Paciente");

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("recusa dataHora fora do formato ISO-8601")
        void recusaDataHoraInvalida() {
            when(pacienteRepository.findById(ID_PACIENTE_JOAO)).thenReturn(Optional.of(joao));
            when(medicoRepository.findById(1L)).thenReturn(Optional.of(carlos));

            assertThatThrownBy(() -> consultaService.criar(
                    new CriarConsultaInput(ID_PACIENTE_JOAO, 1L, "01/08/2026 14:30", null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("ISO-8601");

            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("atualizacao de consulta")
    class Atualizacao {

        @Test
        @DisplayName("cancelamento publica evento CONSULTA_ATUALIZADA com o novo status")
        void cancelamentoPublicaEvento() {
            Consulta consulta = consulta(7L, joao, LocalDateTime.now().plusDays(4));

            when(consultaRepository.findWithDetalhesById(7L)).thenReturn(Optional.of(consulta));
            when(consultaRepository.save(any(Consulta.class))).thenAnswer(i -> i.getArgument(0));

            ConsultaDto atualizada = consultaService.atualizar(7L,
                    new AtualizarConsultaInput(null, StatusConsulta.CANCELADA, "Paciente desmarcou"));

            assertThat(atualizada.status()).isEqualTo(StatusConsulta.CANCELADA);
            assertThat(atualizada.observacoes()).isEqualTo("Paciente desmarcou");

            ArgumentCaptor<ConsultaEvento> capturado = ArgumentCaptor.forClass(ConsultaEvento.class);
            verify(eventPublisher).publicar(capturado.capture());

            ConsultaEvento evento = capturado.getValue();
            assertThat(evento.tipoEvento()).isEqualTo(TipoEvento.CONSULTA_ATUALIZADA);
            // sem o status no evento, o notificacao-service continuaria lembrando uma consulta cancelada
            assertThat(evento.status()).isEqualTo(StatusConsulta.CANCELADA);
        }

        @Test
        @DisplayName("campos nulos preservam os valores atuais")
        void camposNulosSaoPreservados() {
            LocalDateTime dataOriginal = LocalDateTime.now().plusDays(4).withNano(0);
            Consulta consulta = consulta(8L, joao, dataOriginal);

            when(consultaRepository.findWithDetalhesById(8L)).thenReturn(Optional.of(consulta));
            when(consultaRepository.save(any(Consulta.class))).thenAnswer(i -> i.getArgument(0));

            ConsultaDto atualizada = consultaService.atualizar(8L,
                    new AtualizarConsultaInput(null, null, "Apenas uma anotacao"));

            assertThat(atualizada.status()).isEqualTo(StatusConsulta.AGENDADA);
            assertThat(atualizada.dataHora())
                    .isEqualTo(dataOriginal.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            assertThat(atualizada.observacoes()).isEqualTo("Apenas uma anotacao");
        }

        @Test
        @DisplayName("nao deixa uma consulta AGENDADA cair no passado")
        void naoPermiteAgendadaNoPassado() {
            Consulta consulta = consulta(9L, joao, LocalDateTime.now().plusDays(4));
            when(consultaRepository.findWithDetalhesById(9L)).thenReturn(Optional.of(consulta));

            String ontem = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            assertThatThrownBy(() -> consultaService.atualizar(9L,
                    new AtualizarConsultaInput(ontem, null, null)))
                    .isInstanceOf(RegraDeNegocioException.class);

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("permite registrar consulta REALIZADA com data passada")
        void permiteRealizadaComDataPassada() {
            Consulta consulta = consulta(10L, joao, LocalDateTime.now().plusDays(1));
            when(consultaRepository.findWithDetalhesById(10L)).thenReturn(Optional.of(consulta));
            when(consultaRepository.save(any(Consulta.class))).thenAnswer(i -> i.getArgument(0));

            String ontem = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            ConsultaDto atualizada = consultaService.atualizar(10L,
                    new AtualizarConsultaInput(ontem, StatusConsulta.REALIZADA, "Compareceu"));

            assertThat(atualizada.status()).isEqualTo(StatusConsulta.REALIZADA);
            verify(eventPublisher).publicar(any(ConsultaEvento.class));
        }

        @Test
        @DisplayName("recusa consulta inexistente")
        void recusaConsultaInexistente() {
            when(consultaRepository.findWithDetalhesById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> consultaService.atualizar(404L,
                    new AtualizarConsultaInput(null, StatusConsulta.CANCELADA, null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verifyNoInteractions(eventPublisher);
        }
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    private void autenticarComo(Role role, String email) {
        when(contextoSeguranca.usuarioAtual())
                .thenReturn(new UsuarioAutenticado(1L, email, null, role));
    }

    private static Paciente paciente(Long id, String nome, String email) {
        Usuario usuario = new Usuario(nome, email, "$2a$10$hash", Role.PACIENTE);
        Paciente paciente = new Paciente(usuario, "111.111.111-11", "(11) 90000-0000", null);
        ReflectionTestUtils.setField(paciente, "id", id);
        return paciente;
    }

    private static Medico medico() {
        Usuario usuario = new Usuario("Dr. Carlos Andrade", "medico@hospital.com", "$2a$10$hash", Role.MEDICO);
        Medico medico = new Medico(usuario, "CRM-SP-123456", "Cardiologia");
        ReflectionTestUtils.setField(medico, "id", 1L);
        return medico;
    }

    private static Consulta consulta(Long id, Paciente paciente, LocalDateTime dataHora) {
        Consulta consulta = new Consulta(paciente, medico(), dataHora, StatusConsulta.AGENDADA, "Observacao");
        ReflectionTestUtils.setField(consulta, "id", id);
        ReflectionTestUtils.setField(consulta, "criadoEm", LocalDateTime.now());
        ReflectionTestUtils.setField(consulta, "atualizadoEm", LocalDateTime.now());
        return consulta;
    }
}
