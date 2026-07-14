package br.com.fiap.notificacao;

import br.com.fiap.notificacao.repository.NotificacaoRepository;
import br.com.fiap.notificacao.service.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o contexto completo contra H2 com as migracoes reais: valida de uma vez o
 * mapeamento JPA (ddl-auto=validate), a topologia Rabbit declarada e o agendador.
 * O {@link EmailSender} e mockado para nao exigir um SMTP no build.
 */
@SpringBootTest
@DisplayName("Contexto do notificacao-service")
class ContextoAplicacaoTest {

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @MockitoBean
    private EmailSender emailSender;

    @Test
    @DisplayName("contexto sobe e o schema das migracoes bate com as entidades")
    void contextoSobeComSchemaValido() {
        assertThat(notificacaoRepository.count()).isZero();
    }
}
