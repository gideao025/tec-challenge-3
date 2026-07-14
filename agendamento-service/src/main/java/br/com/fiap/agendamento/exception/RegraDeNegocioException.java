package br.com.fiap.agendamento.exception;

/** Violacao de invariante de dominio (ex.: agendar consulta no passado). */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
