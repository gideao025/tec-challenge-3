package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.StatusConsulta;

import java.time.format.DateTimeFormatter;

/**
 * Contrato da mensagem publicada em {@code consultas.exchange}.
 *
 * <p>O evento carrega os dados que o notificacao-service precisa para montar o
 * lembrete (nome/e-mail do paciente, medico, data), de modo que ele nunca precise
 * consultar o banco do agendamento — os servicos permanecem desacoplados.
 *
 * <p>{@code status} nao estava no rascunho original do payload, mas e indispensavel:
 * sem ele, o notificacao-service nao teria como parar de lembrar uma consulta que
 * foi CANCELADA.
 */
public record ConsultaEvento(
        Long consultaId,
        String pacienteNome,
        String pacienteEmail,
        String dataHora,
        String medicoNome,
        StatusConsulta status,
        TipoEvento tipoEvento) {

    public static ConsultaEvento de(Consulta consulta, TipoEvento tipoEvento) {
        return new ConsultaEvento(
                consulta.getId(),
                consulta.getPaciente().getNome(),
                consulta.getPaciente().getEmail(),
                consulta.getDataHora().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                consulta.getMedico().getNome(),
                consulta.getStatus(),
                tipoEvento);
    }
}
