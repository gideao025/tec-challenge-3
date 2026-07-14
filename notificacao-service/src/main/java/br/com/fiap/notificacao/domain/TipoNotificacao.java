package br.com.fiap.notificacao.domain;

public enum TipoNotificacao {

    /** Consulta acabou de ser registrada. */
    CONFIRMACAO,

    /** Consulta foi remarcada, cancelada ou teve o status alterado. */
    ATUALIZACAO,

    /** Lembrete disparado pelo agendador para consultas nas proximas 24h. */
    LEMBRETE_24H
}
