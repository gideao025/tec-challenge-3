package br.com.fiap.agendamento.domain;

/**
 * Perfis de acesso do sistema hospitalar.
 *
 * <p>As authorities do Spring Security sao geradas com o prefixo {@code ROLE_}
 * (ex.: {@code ROLE_MEDICO}), permitindo o uso de {@code hasRole('MEDICO')}.
 */
public enum Role {

    /** Visualiza e edita o historico de consultas. */
    MEDICO,

    /** Registra novas consultas e acessa o historico. */
    ENFERMEIRO,

    /** Visualiza apenas as proprias consultas. */
    PACIENTE;

    public String authority() {
        return "ROLE_" + name();
    }
}
