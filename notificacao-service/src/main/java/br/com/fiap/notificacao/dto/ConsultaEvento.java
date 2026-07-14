package br.com.fiap.notificacao.dto;

/**
 * Contrato da mensagem consumida de {@code consultas.exchange}.
 *
 * <p>O record e intencionalmente duplicado (nao compartilhado num modulo comum com o
 * agendamento-service): microsservicos que compartilham uma classe de contrato passam
 * a compartilhar um ciclo de deploy. Aqui cada lado evolui o seu proprio schema, e o
 * acoplamento fica so no formato JSON.
 *
 * <p>Campos desconhecidos sao ignorados na desserializacao, entao o produtor pode
 * adicionar campos novos sem quebrar este consumidor.
 */
public record ConsultaEvento(
        Long consultaId,
        String pacienteNome,
        String pacienteEmail,
        String dataHora,
        String medicoNome,
        StatusConsulta status,
        TipoEvento tipoEvento) {
}
