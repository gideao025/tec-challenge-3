package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.Medico;

public record MedicoDto(Long id, String nome, String crm, String especialidade) {

    public static MedicoDto de(Medico medico) {
        return new MedicoDto(
                medico.getId(),
                medico.getNome(),
                medico.getCrm(),
                medico.getEspecialidade());
    }
}
