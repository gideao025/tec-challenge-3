package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.Role;

public record LoginResponse(
        String token,
        String tipo,
        long expiraEmSegundos,
        String nome,
        String email,
        Role role) {

    public static LoginResponse bearer(String token, long expiraEmSegundos,
                                       String nome, String email, Role role) {
        return new LoginResponse(token, "Bearer", expiraEmSegundos, nome, email, role);
    }
}
