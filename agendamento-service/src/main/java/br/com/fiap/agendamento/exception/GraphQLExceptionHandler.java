package br.com.fiap.agendamento.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Traduz excecoes de dominio/seguranca em erros GraphQL classificados.
 *
 * <p>Detalhe importante: GraphQL responde HTTP 200 mesmo em falha — o que distingue
 * um acesso negado e a {@code classification} do erro. Por isso mapeamos
 * {@link AccessDeniedException} para {@code FORBIDDEN} (e para {@code UNAUTHORIZED}
 * quando nem ha usuario autenticado), que e o equivalente GraphQL do 403/401.
 */
@Component
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    public GraphQLExceptionHandler() {
        setThreadLocalContextAware(true);
    }

    @Override
    protected GraphQLError resolveToSingleError(@NonNull Throwable excecao,
                                                @NonNull DataFetchingEnvironment ambiente) {
        return switch (excecao) {
            case AccessDeniedException e -> erro(e.getMessage(), classificarAcessoNegado(), ambiente);
            case RecursoNaoEncontradoException e -> erro(e.getMessage(), ErrorType.NOT_FOUND, ambiente);
            case RegraDeNegocioException e -> erro(e.getMessage(), ErrorType.BAD_REQUEST, ambiente);
            case IllegalArgumentException e -> erro(e.getMessage(), ErrorType.BAD_REQUEST, ambiente);
            // Bean Validation nos @Argument: e erro de quem chamou, nao falha do servidor.
            case ConstraintViolationException e -> erro(mensagemDe(e), ErrorType.BAD_REQUEST, ambiente);
            default -> null; // deixa o Spring GraphQL tratar como INTERNAL_ERROR
        };
    }

    /** Junta as violacoes em uma mensagem util, sem vazar o nome do metodo do resolver. */
    private String mensagemDe(ConstraintViolationException excecao) {
        return excecao.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining("; "));
    }

    /** Sem principal no contexto, o problema e falta de autenticacao, nao de permissao. */
    private ErrorType classificarAcessoNegado() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        boolean anonimo = autenticacao == null || !autenticacao.isAuthenticated();
        return anonimo ? ErrorType.UNAUTHORIZED : ErrorType.FORBIDDEN;
    }

    private GraphQLError erro(String mensagem, ErrorType tipo, DataFetchingEnvironment ambiente) {
        return GraphqlErrorBuilder.newError(ambiente)
                .errorType(tipo)
                .message(mensagem == null ? "Requisicao invalida" : mensagem)
                .build();
    }
}
