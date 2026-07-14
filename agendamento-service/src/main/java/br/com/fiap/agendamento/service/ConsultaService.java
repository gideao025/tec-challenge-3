package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.Medico;
import br.com.fiap.agendamento.domain.Paciente;
import br.com.fiap.agendamento.domain.StatusConsulta;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Regras de agendamento e do historico de consultas.
 *
 * <p>A autorizacao acontece em duas camadas: os resolvers barram por role
 * ({@code @PreAuthorize}), e este service aplica a regra de <b>ownership</b> — que
 * depende do dado, e nao apenas do perfil. Mantendo o ownership aqui, ele vale para
 * qualquer porta de entrada (GraphQL hoje, REST/gRPC amanha).
 */
@Service
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;
    private final ContextoSeguranca contextoSeguranca;
    private final EventPublisher eventPublisher;

    public ConsultaService(ConsultaRepository consultaRepository,
                           PacienteRepository pacienteRepository,
                           MedicoRepository medicoRepository,
                           ContextoSeguranca contextoSeguranca,
                           EventPublisher eventPublisher) {
        this.consultaRepository = consultaRepository;
        this.pacienteRepository = pacienteRepository;
        this.medicoRepository = medicoRepository;
        this.contextoSeguranca = contextoSeguranca;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Historico de consultas de um paciente.
     *
     * <p>Para um usuario PACIENTE o filtro e forcado para o proprio prontuario:
     * pedir o historico de outro paciente resulta em acesso negado, e omitir o
     * pacienteId simplesmente devolve o seu.
     */
    @Transactional(readOnly = true)
    public List<ConsultaDto> buscarPorPaciente(Long pacienteIdSolicitado, boolean apenasFuturas) {
        Long pacienteId = resolverPacienteAlvo(pacienteIdSolicitado);

        List<Consulta> consultas = apenasFuturas
                ? consultaRepository.findByPacienteIdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
                        pacienteId, LocalDateTime.now())
                : consultaRepository.findByPacienteIdOrderByDataHoraDesc(pacienteId);

        return consultas.stream().map(ConsultaDto::de).toList();
    }

    @Transactional(readOnly = true)
    public ConsultaDto buscarPorId(Long id) {
        Consulta consulta = consultaRepository.findWithDetalhesById(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Consulta", id));

        garantirAcessoAConsulta(consulta);

        return ConsultaDto.de(consulta);
    }

    /** Registro de nova consulta (MEDICO ou ENFERMEIRO). Nasce sempre AGENDADA. */
    @Transactional
    public ConsultaDto criar(CriarConsultaInput input) {
        Paciente paciente = pacienteRepository.findById(input.pacienteId())
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Paciente", input.pacienteId()));

        Medico medico = medicoRepository.findById(input.medicoId())
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Medico", input.medicoId()));

        LocalDateTime dataHora = parsearDataHora(input.dataHora());
        if (dataHora.isBefore(LocalDateTime.now())) {
            throw new RegraDeNegocioException("Nao e possivel agendar uma consulta no passado");
        }

        Consulta consulta = consultaRepository.save(
                new Consulta(paciente, medico, dataHora, StatusConsulta.AGENDADA, input.observacoes()));

        eventPublisher.publicar(ConsultaEvento.de(consulta, TipoEvento.CONSULTA_CRIADA));

        return ConsultaDto.de(consulta);
    }

    /** Edicao do historico (apenas MEDICO). Atualizacao parcial: nulos sao ignorados. */
    @Transactional
    public ConsultaDto atualizar(Long id, AtualizarConsultaInput input) {
        Consulta consulta = consultaRepository.findWithDetalhesById(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Consulta", id));

        if (input.dataHora() != null) {
            consulta.setDataHora(parsearDataHora(input.dataHora()));
        }
        if (input.status() != null) {
            consulta.setStatus(input.status());
        }
        if (input.observacoes() != null) {
            consulta.setObservacoes(input.observacoes());
        }

        // Reagendar para o passado so faz sentido em consultas ja encerradas.
        if (consulta.getStatus() == StatusConsulta.AGENDADA
                && consulta.getDataHora().isBefore(LocalDateTime.now())) {
            throw new RegraDeNegocioException(
                    "Uma consulta AGENDADA nao pode ficar com data no passado");
        }

        Consulta salva = consultaRepository.save(consulta);

        eventPublisher.publicar(ConsultaEvento.de(salva, TipoEvento.CONSULTA_ATUALIZADA));

        return ConsultaDto.de(salva);
    }

    /**
     * Decide qual paciente sera consultado, aplicando o ownership.
     *
     * <p>MEDICO/ENFERMEIRO consultam qualquer paciente (mas precisam dizer qual).
     * PACIENTE fica preso ao proprio id.
     */
    private Long resolverPacienteAlvo(Long pacienteIdSolicitado) {
        UsuarioAutenticado usuario = contextoSeguranca.usuarioAtual();

        if (!usuario.isPaciente()) {
            if (pacienteIdSolicitado == null) {
                throw new IllegalArgumentException(
                        "pacienteId e obrigatorio para os perfis MEDICO e ENFERMEIRO");
            }
            return pacienteIdSolicitado;
        }

        Long proprioId = pacienteDoUsuarioAutenticado();

        if (pacienteIdSolicitado != null && !pacienteIdSolicitado.equals(proprioId)) {
            throw new AccessDeniedException(
                    "Um paciente so pode consultar o proprio historico de consultas");
        }
        return proprioId;
    }

    /** Um PACIENTE so enxerga consultas cujo paciente e ele mesmo. */
    private void garantirAcessoAConsulta(Consulta consulta) {
        UsuarioAutenticado usuario = contextoSeguranca.usuarioAtual();

        if (usuario.isPaciente() && !consulta.getPaciente().getId().equals(pacienteDoUsuarioAutenticado())) {
            throw new AccessDeniedException(
                    "Um paciente so pode acessar as proprias consultas");
        }
    }

    private Long pacienteDoUsuarioAutenticado() {
        UsuarioAutenticado usuario = contextoSeguranca.usuarioAtual();

        return pacienteRepository.findByUsuarioEmail(usuario.getEmail())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuario autenticado nao possui cadastro de paciente: " + usuario.getEmail()))
                .getId();
    }

    private LocalDateTime parsearDataHora(String valor) {
        try {
            return LocalDateTime.parse(valor);
        } catch (DateTimeParseException e) {
            throw new RegraDeNegocioException(
                    "dataHora invalida: '%s'. Use o formato ISO-8601, ex.: 2026-08-01T14:30:00"
                            .formatted(valor));
        }
    }
}
