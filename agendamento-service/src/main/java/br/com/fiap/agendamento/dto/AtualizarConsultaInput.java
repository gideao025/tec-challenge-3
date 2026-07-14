package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.StatusConsulta;

/**
 * Atualizacao parcial: campos nulos sao mantidos como estao. Isso permite, por
 * exemplo, apenas cancelar a consulta sem reenviar data e observacoes.
 */
public record AtualizarConsultaInput(
        String dataHora,
        StatusConsulta status,
        String observacoes) {
}
