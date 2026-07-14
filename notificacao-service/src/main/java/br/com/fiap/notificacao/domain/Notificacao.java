package br.com.fiap.notificacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Registro auditavel de cada lembrete/aviso efetivamente enviado (ou que falhou). */
@Entity
@Table(name = "notificacao")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consulta_id", nullable = false)
    private Long consultaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoNotificacao tipo;

    @Column(nullable = false)
    private String destinatario;

    @Column(nullable = false)
    private String assunto;

    @Column(nullable = false, length = 2000)
    private String mensagem;

    /**
     * Data da consulta que originou a notificacao. Junto de (consulta_id, tipo) forma a
     * chave de idempotencia: se a consulta for remarcada, um novo lembrete e permitido;
     * se o agendador rodar duas vezes para a mesma data, o segundo envio e barrado.
     */
    @Column(name = "data_hora_consulta", nullable = false)
    private LocalDateTime dataHoraConsulta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEnvio status;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    @Column(length = 500)
    private String erro;

    protected Notificacao() {
    }

    private Notificacao(Long consultaId, TipoNotificacao tipo, String destinatario, String assunto,
                        String mensagem, LocalDateTime dataHoraConsulta, StatusEnvio status, String erro) {
        this.consultaId = consultaId;
        this.tipo = tipo;
        this.destinatario = destinatario;
        this.assunto = assunto;
        this.mensagem = mensagem;
        this.dataHoraConsulta = dataHoraConsulta;
        this.status = status;
        this.erro = erro;
        this.enviadoEm = LocalDateTime.now();
    }

    public static Notificacao enviada(Long consultaId, TipoNotificacao tipo, String destinatario,
                                      String assunto, String mensagem, LocalDateTime dataHoraConsulta) {
        return new Notificacao(consultaId, tipo, destinatario, assunto, mensagem,
                dataHoraConsulta, StatusEnvio.ENVIADA, null);
    }

    public static Notificacao falha(Long consultaId, TipoNotificacao tipo, String destinatario,
                                    String assunto, String mensagem, LocalDateTime dataHoraConsulta,
                                    String erro) {
        return new Notificacao(consultaId, tipo, destinatario, assunto, mensagem,
                dataHoraConsulta, StatusEnvio.FALHA, truncar(erro));
    }

    private static String truncar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= 500 ? texto : texto.substring(0, 500);
    }

    public Long getId() {
        return id;
    }

    public Long getConsultaId() {
        return consultaId;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getAssunto() {
        return assunto;
    }

    public String getMensagem() {
        return mensagem;
    }

    public LocalDateTime getDataHoraConsulta() {
        return dataHoraConsulta;
    }

    public StatusEnvio getStatus() {
        return status;
    }

    public LocalDateTime getEnviadoEm() {
        return enviadoEm;
    }

    public String getErro() {
        return erro;
    }

    public enum StatusEnvio {
        ENVIADA,
        FALHA
    }
}
