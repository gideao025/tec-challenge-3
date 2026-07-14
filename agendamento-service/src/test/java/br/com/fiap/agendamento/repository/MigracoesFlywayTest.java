package br.com.fiap.agendamento.repository;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.Role;
import br.com.fiap.agendamento.domain.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que as migracoes Flyway rodam e que o mapeamento JPA valida contra o
 * schema gerado por elas (ddl-auto=validate). Se uma entidade divergir da DDL,
 * este teste quebra.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MigracoesFlywayTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConsultaRepository consultaRepository;

    @Test
    @DisplayName("seed cria um usuario para cada role")
    void seedCriaUsuarioPorRole() {
        assertThat(usuarioRepository.findAll())
                .extracting(Usuario::getRole)
                .contains(Role.MEDICO, Role.ENFERMEIRO, Role.PACIENTE);
    }

    @Test
    @DisplayName("usuario do seed e encontrado por e-mail e tem hash BCrypt")
    void usuarioDoSeedTemHashBcrypt() {
        Optional<Usuario> medico = usuarioRepository.findByEmail("medico@hospital.com");

        assertThat(medico).isPresent();
        assertThat(medico.get().getSenha()).startsWith("$2a$");
        assertThat(medico.get().getRole()).isEqualTo(Role.MEDICO);
    }

    @Test
    @DisplayName("consultas do seed carregam paciente e medico pelo entity graph")
    void consultasDoSeedCarregamRelacionamentos() {
        List<Consulta> consultas = consultaRepository.findByPacienteIdOrderByDataHoraDesc(1L);

        assertThat(consultas).hasSize(3);
        assertThat(consultas.getFirst().getPaciente().getNome()).isEqualTo("Joao Souza");
        assertThat(consultas.getFirst().getMedico().getNome()).isEqualTo("Dr. Carlos Andrade");
    }
}
