package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.Paciente;

public record PacienteDto(Long id, String nome, String email, String cpf) {

    public static PacienteDto de(Paciente paciente) {
        return new PacienteDto(
                paciente.getId(),
                paciente.getNome(),
                paciente.getEmail(),
                paciente.getCpf());
    }
}
