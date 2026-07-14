package br.com.fiap.agendamento;

import br.com.fiap.agendamento.service.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integracao do fluxo de seguranca ponta a ponta:
 * login real (BCrypt do seed Flyway) -> chamada autorizada -> acesso negado por role.
 *
 * <p>Sobe o contexto inteiro contra H2 executando as migracoes de verdade. O
 * {@link EventPublisher} e mockado: o objetivo aqui e seguranca, nao mensageria —
 * e assim o teste roda sem depender de um broker.
 *
 * <p>Sobre os codigos: o GraphQL sempre responde HTTP 200 e sinaliza a falha no array
 * {@code errors}. Portanto o equivalente ao "403 para role errada" e a
 * {@code classification: FORBIDDEN} — que e exatamente o que asseguramos abaixo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // cada teste roda em transacao revertida ao final, isolando a massa do seed
@DisplayName("Fluxo de seguranca (login -> autorizado -> negado)")
class SegurancaFluxoIntegrationTest {

    private static final String SENHA = "senha123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventPublisher eventPublisher;

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    @Test
    @DisplayName("login com credenciais validas devolve um JWT para cada role")
    void loginDasTresRoles() throws Exception {
        for (String email : new String[]{
                "medico@hospital.com", "enfermeiro@hospital.com", "paciente@hospital.com"}) {

            mockMvc.perform(post("/auth/login")
                            .contentType("application/json")
                            .content(json(Map.of("email", email, "senha", SENHA))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tipo").value("Bearer"))
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.email").value(email));
        }
    }

    @Test
    @DisplayName("login com senha errada devolve 401")
    void loginComSenhaErrada() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(json(Map.of("email", "medico@hospital.com", "senha", "errada"))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Acesso autorizado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("medico autenticado consulta o historico de um paciente")
    void medicoConsultaHistorico() throws Exception {
        String token = autenticar("medico@hospital.com");

        graphql(token, """
                query { consultasPorPaciente(pacienteId: 1) { id status paciente { nome } } }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.consultasPorPaciente", hasSize(3)))
                .andExpect(jsonPath("$.data.consultasPorPaciente[*].paciente.nome",
                        everyItem(is("Joao Souza"))));
    }

    @Test
    @DisplayName("enfermeiro autenticado registra uma nova consulta")
    void enfermeiroCriaConsulta() throws Exception {
        String token = autenticar("enfermeiro@hospital.com");
        String dataHora = LocalDateTime.now().plusDays(10).withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        graphql(token, """
                mutation {
                  criarConsulta(input: {pacienteId: 1, medicoId: 1, dataHora: "%s", observacoes: "Nova"}) {
                    id status observacoes
                  }
                }
                """.formatted(dataHora))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.criarConsulta.status").value("AGENDADA"));
    }

    @Test
    @DisplayName("paciente ve apenas as proprias consultas quando omite o pacienteId")
    void pacienteVeApenasAsProprias() throws Exception {
        String token = autenticar("paciente@hospital.com");

        graphql(token, """
                query { consultasPorPaciente { id paciente { id nome } } }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.consultasPorPaciente", hasSize(3)))
                .andExpect(jsonPath("$.data.consultasPorPaciente[*].paciente.id",
                        everyItem(is("1"))));
    }

    // ------------------------------------------------------------------
    // Acesso negado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sem token: 401 no GraphQL")
    void semTokenRecebe401() throws Exception {
        mockMvc.perform(post("/graphql")
                        .contentType("application/json")
                        .content(json(Map.of("query", "{ consultasPorPaciente(pacienteId: 1) { id } }"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("role errada: paciente nao pode criar consulta (FORBIDDEN)")
    void pacienteNaoCriaConsulta() throws Exception {
        String token = autenticar("paciente@hospital.com");
        String dataHora = LocalDateTime.now().plusDays(3).withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        graphql(token, """
                mutation {
                  criarConsulta(input: {pacienteId: 1, medicoId: 1, dataHora: "%s"}) { id }
                }
                """.formatted(dataHora))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.criarConsulta").doesNotExist())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("role errada: enfermeiro nao pode editar o historico (FORBIDDEN)")
    void enfermeiroNaoEditaHistorico() throws Exception {
        String token = autenticar("enfermeiro@hospital.com");

        graphql(token, """
                mutation { atualizarConsulta(id: 1, input: {status: CANCELADA}) { id status } }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.atualizarConsulta").doesNotExist())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ownership: paciente nao acessa o historico de outro paciente (FORBIDDEN)")
    void pacienteNaoAcessaHistoricoDeOutro() throws Exception {
        String token = autenticar("paciente@hospital.com");

        // Joao (paciente 1) tentando ler o prontuario da Maria (paciente 2)
        graphql(token, """
                query { consultasPorPaciente(pacienteId: 2) { id observacoes } }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"))
                .andExpect(jsonPath("$.errors[0].message", containsString("proprio historico")));
    }

    @Test
    @DisplayName("ownership: paciente nao acessa consulta alheia pelo id (FORBIDDEN)")
    void pacienteNaoAcessaConsultaAlheiaPorId() throws Exception {
        String token = autenticar("paciente@hospital.com");

        // a consulta 4 pertence a Maria
        graphql(token, """
                query { consulta(id: 4) { id observacoes } }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("input invalido vira BAD_REQUEST, e nao erro interno")
    void inputInvalidoViraBadRequest() throws Exception {
        String token = autenticar("enfermeiro@hospital.com");

        // dataHora em branco: viola o @NotBlank do input
        graphql(token, """
                mutation {
                  criarConsulta(input: {pacienteId: 1, medicoId: 1, dataHora: ""}) { id }
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("token adulterado e rejeitado com 401")
    void tokenAdulteradoRecebe401() throws Exception {
        String token = autenticar("medico@hospital.com");
        String adulterado = token.substring(0, token.lastIndexOf('.') + 1) + "assinaturaFalsa";

        mockMvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + adulterado)
                        .contentType("application/json")
                        .content(json(Map.of("query", "{ consultasPorPaciente(pacienteId: 1) { id } }"))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** Faz o login de verdade e devolve o JWT emitido. */
    private String autenticar(String email) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(json(Map.of("email", email, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(corpo).get("token").asText();
    }

    private ResultActions graphql(String token, String query) throws Exception {
        return mockMvc.perform(post("/graphql")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json(Map.of("query", query))));
    }

    private String json(Object valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }
}
