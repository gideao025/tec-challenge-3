package br.com.fiap.notificacao.repository;

import br.com.fiap.notificacao.domain.Notificacao;
import br.com.fiap.notificacao.domain.TipoNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /**
     * Guarda de idempotencia do agendador: evita reenviar o mesmo lembrete se o job
     * rodar novamente para a mesma consulta na mesma data/hora.
     */
    boolean existsByConsultaIdAndTipoAndDataHoraConsultaAndStatus(
            Long consultaId, TipoNotificacao tipo, LocalDateTime dataHoraConsulta,
            Notificacao.StatusEnvio status);

    List<Notificacao> findByConsultaIdOrderByEnviadoEmDesc(Long consultaId);
}
