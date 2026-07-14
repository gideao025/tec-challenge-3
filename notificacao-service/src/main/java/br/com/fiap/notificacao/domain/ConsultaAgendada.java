package br.com.fiap.notificacao.domain;

import br.com.fiap.notificacao.dto.StatusConsulta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Projecao local das consultas, alimentada exclusivamente pelos eventos do
 * agendamento-service.
 *
 * <p>E o que torna o @Scheduled possivel sem violar a autonomia dos servicos: para
 * saber quais consultas ocorrem nas proximas 24h, este servico le a <b>propria</b>
 * base, em vez de abrir uma conexao no banco do agendamento (que acoplaria os dois
 * schemas) ou consultar sua API a cada ciclo.
 *
 * <p>O id e o mesmo da consulta na origem, o que torna o consumo idempotente:
 * reprocessar um evento apenas sobrescreve a projecao.
 */
@Entity
@Table(name = "consulta_agendada")
public class ConsultaAgendada {

    /** Id vindo do agendamento-service (nao e gerado aqui). */
    @Id
    private Long id;

    @Column(name = "paciente_nome", nullable = false)
    private String pacienteNome;

    @Column(name = "paciente_email", nullable = false)
    private String pacienteEmail;

    @Column(name = "medico_nome", nullable = false)
    private String medicoNome;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConsulta status;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected ConsultaAgendada() {
    }

    public ConsultaAgendada(Long id, String pacienteNome, String pacienteEmail,
                            String medicoNome, LocalDateTime dataHora, StatusConsulta status) {
        this.id = id;
        this.pacienteNome = pacienteNome;
        this.pacienteEmail = pacienteEmail;
        this.medicoNome = medicoNome;
        this.dataHora = dataHora;
        this.status = status;
        this.atualizadoEm = LocalDateTime.now();
    }

    /** Aplica um evento posterior sobre a projecao. */
    public void atualizar(String pacienteNome, String pacienteEmail, String medicoNome,
                          LocalDateTime dataHora, StatusConsulta status) {
        this.pacienteNome = pacienteNome;
        this.pacienteEmail = pacienteEmail;
        this.medicoNome = medicoNome;
        this.dataHora = dataHora;
        this.status = status;
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getPacienteNome() {
        return pacienteNome;
    }

    public String getPacienteEmail() {
        return pacienteEmail;
    }

    public String getMedicoNome() {
        return medicoNome;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public StatusConsulta getStatus() {
        return status;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
