package br.com.fiap.notificacao.repository;

import br.com.fiap.notificacao.domain.ConsultaAgendada;
import br.com.fiap.notificacao.dto.StatusConsulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ConsultaAgendadaRepository extends JpaRepository<ConsultaAgendada, Long> {

    /** Consultas da janela de lembrete. Consultas CANCELADAS/REALIZADAS nao entram. */
    List<ConsultaAgendada> findByStatusAndDataHoraBetweenOrderByDataHoraAsc(
            StatusConsulta status, LocalDateTime inicio, LocalDateTime fim);
}
