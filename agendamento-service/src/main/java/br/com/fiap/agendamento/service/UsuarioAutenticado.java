package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Role;
import br.com.fiap.agendamento.domain.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal autenticado. Carrega o id e a role do usuario para que as regras de
 * autorizacao (inclusive o ownership do paciente) nao precisem reconsultar o banco.
 */
public class UsuarioAutenticado implements UserDetails {

    private final Long id;
    private final String email;
    private final String senha;
    private final Role role;

    public UsuarioAutenticado(Long id, String email, String senha, Role role) {
        this.id = id;
        this.email = email;
        this.senha = senha;
        this.role = role;
    }

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), usuario.getSenha(), usuario.getRole());
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isPaciente() {
        return role == Role.PACIENTE;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
