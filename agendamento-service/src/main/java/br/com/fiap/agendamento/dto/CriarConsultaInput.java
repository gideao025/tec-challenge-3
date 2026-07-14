package br.com.fiap.agendamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CriarConsultaInput(

        @NotNull(message = "pacienteId e obrigatorio")
        Long pacienteId,

        @NotNull(message = "medicoId e obrigatorio")
        Long medicoId,

        @NotBlank(message = "dataHora e obrigatoria (ISO-8601, ex.: 2026-08-01T14:30:00)")
        String dataHora,

        String observacoes) {
}
