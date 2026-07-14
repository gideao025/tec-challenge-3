package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.StatusConsulta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Projecao de {@link Consulta} exposta pelo GraphQL.
 *
 * <p>Datas trafegam como String ISO-8601: o schema usa o scalar String nativo, o que
 * evita trazer a dependencia {@code graphql-java-extended-scalars} so por causa de
 * um tipo DateTime.
 */
public record ConsultaDto(
        Long id,
        PacienteDto paciente,
        MedicoDto medico,
        String dataHora,
        StatusConsulta status,
        String observacoes,
        String criadoEm,
        String atualizadoEm) {

    public static ConsultaDto de(Consulta consulta) {
        return new ConsultaDto(
                consulta.getId(),
                PacienteDto.de(consulta.getPaciente()),
                MedicoDto.de(consulta.getMedico()),
                formatar(consulta.getDataHora()),
                consulta.getStatus(),
                consulta.getObservacoes(),
                formatar(consulta.getCriadoEm()),
                formatar(consulta.getAtualizadoEm()));
    }

    private static String formatar(LocalDateTime dataHora) {
        return dataHora == null ? null : dataHora.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
