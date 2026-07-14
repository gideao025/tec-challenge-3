package br.com.fiap.agendamento.repository;

import br.com.fiap.agendamento.domain.Consulta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    /*
     * As associacoes de Consulta sao LAZY; o @EntityGraph carrega paciente/medico
     * (e seus usuarios) na mesma query, evitando N+1 ao montar os DTOs.
     */

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "medico", "medico.usuario"})
    Optional<Consulta> findWithDetalhesById(Long id);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "medico", "medico.usuario"})
    List<Consulta> findByPacienteIdOrderByDataHoraDesc(Long pacienteId);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "medico", "medico.usuario"})
    List<Consulta> findByPacienteIdAndDataHoraGreaterThanEqualOrderByDataHoraAsc(
            Long pacienteId, LocalDateTime inicio);
}
