package br.com.fiap.agendamento.repository;

import br.com.fiap.agendamento.domain.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /** Base da regra de ownership: resolve o paciente a partir do usuario autenticado. */
    Optional<Paciente> findByUsuarioEmail(String email);
}
