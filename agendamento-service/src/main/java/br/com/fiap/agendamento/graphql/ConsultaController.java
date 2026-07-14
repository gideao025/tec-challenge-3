package br.com.fiap.agendamento.graphql;

import br.com.fiap.agendamento.dto.AtualizarConsultaInput;
import br.com.fiap.agendamento.dto.ConsultaDto;
import br.com.fiap.agendamento.dto.CriarConsultaInput;
import br.com.fiap.agendamento.service.ConsultaService;
import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * Resolvers GraphQL das consultas.
 *
 * <p>A autorizacao por perfil fica nas anotacoes {@code @PreAuthorize} de cada
 * operacao — necessario porque todo o GraphQL trafega por um unico POST /graphql,
 * entao nao da para autorizar por URL. A regra de ownership do paciente (que depende
 * do dado, nao so do perfil) fica no {@link ConsultaService}.
 */
@Controller
public class ConsultaController {

    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    /** Historico: os tres perfis leem, mas o PACIENTE so enxerga o proprio. */
    @QueryMapping
    @PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO', 'PACIENTE')")
    public List<ConsultaDto> consultasPorPaciente(@Argument Long pacienteId,
                                                  @Argument Boolean apenasFuturas) {
        return consultaService.buscarPorPaciente(pacienteId, Boolean.TRUE.equals(apenasFuturas));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO', 'PACIENTE')")
    public ConsultaDto consulta(@Argument Long id) {
        return consultaService.buscarPorId(id);
    }

    /** Enfermeiros registram consultas; medicos tambem podem. Paciente, nunca. */
    @MutationMapping
    @PreAuthorize("hasAnyRole('MEDICO', 'ENFERMEIRO')")
    public ConsultaDto criarConsulta(@Argument @Valid CriarConsultaInput input) {
        return consultaService.criar(input);
    }

    /** Editar o historico e prerrogativa exclusiva do medico. */
    @MutationMapping
    @PreAuthorize("hasRole('MEDICO')")
    public ConsultaDto atualizarConsulta(@Argument Long id,
                                         @Argument @Valid AtualizarConsultaInput input) {
        return consultaService.atualizar(id, input);
    }
}
