package br.com.fiap.agendamento;

import br.com.fiap.agendamento.service.EventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o servidor web de verdade (Tomcat, porta aleatoria) e fala HTTP com ele.
 *
 * <p>Complementa o {@code SegurancaFluxoIntegrationTest}, que usa MockMvc e portanto
 * curto-circuita o container: aqui o JSON e serializado, o header Authorization
 * atravessa a rede e a cadeia de filtros roda no servlet container real. E a prova de
 * que a aplicacao efetivamente serve requisicoes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Servidor HTTP real (Tomcat)")
class ServidorHttpRealIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @MockitoBean
    private EventPublisher eventPublisher;

    @Test
    @DisplayName("login por HTTP devolve o JWT e o token abre o GraphQL")
    void loginEConsultaSobreHttpReal() {
        // 1) login de verdade, sobre HTTP
        ResponseEntity<JsonNode> login = rest.postForEntity(
                "/auth/login",
                new HttpEntity<>(Map.of("email", "medico@hospital.com", "senha", "senha123"),
                        headers(null)),
                JsonNode.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login.getBody().get("token").asText();
        assertThat(token).isNotBlank();

        // 2) o token abre a query GraphQL
        ResponseEntity<JsonNode> consulta = rest.postForEntity(
                "/graphql",
                new HttpEntity<>(
                        Map.of("query", "{ consultasPorPaciente(pacienteId: 1) { id status } }"),
                        headers(token)),
                JsonNode.class);

        assertThat(consulta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consulta.getBody().get("errors")).isNull();
        assertThat(consulta.getBody().get("data").get("consultasPorPaciente")).isNotEmpty();
    }

    @Test
    @DisplayName("sem token o servidor responde 401")
    void semTokenResponde401() {
        ResponseEntity<String> resposta = rest.postForEntity(
                "/graphql",
                new HttpEntity<>(
                        Map.of("query", "{ consultasPorPaciente(pacienteId: 1) { id } }"),
                        headers(null)),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
