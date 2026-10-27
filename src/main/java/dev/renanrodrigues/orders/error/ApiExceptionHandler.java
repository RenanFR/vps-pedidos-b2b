package dev.renanrodrigues.orders.error;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://api.pedidos-b2b.dev/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Recurso não encontrado", exception.getMessage());
    }

    @ExceptionHandler(InsufficientCreditException.class)
    public ProblemDetail handleInsufficientCredit(InsufficientCreditException exception) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-credit",
                "Crédito insuficiente", exception.getMessage());
        problem.setProperty("requiredAmount", exception.getRequiredAmount());
        problem.setProperty("availableCredit", exception.getAvailableCredit());
        return problem;
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidStatusTransitionException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "invalid-status-transition",
                "Transição de status não permitida", exception.getMessage());
        problem.setProperty("currentStatus", exception.getCurrentStatus());
        problem.setProperty("requestedStatus", exception.getRequestedStatus());
        return problem;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException exception) {
        return problem(HttpStatus.CONFLICT, "idempotency-conflict", "Chave de idempotência reutilizada",
                exception.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ProblemDetail handleConcurrentUpdate(RuntimeException exception) {
        log.warn("Atualização concorrente recusada. Motivo: {}", exception.getMessage());
        return problem(HttpStatus.CONFLICT, "concurrent-update", "Atualização concorrente",
                "O pedido foi alterado por outra requisição ao mesmo tempo. Consulte o pedido e tente de novo.");
    }

    @ExceptionHandler({InvalidRequestException.class, PropertyReferenceException.class})
    public ProblemDetail handleInvalidRequest(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Requisição inválida", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Requisição inválida",
                "O valor [" + exception.getValue() + "] não é válido para o parâmetro " + exception.getName());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Erro inesperado ao atender a requisição", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Erro interno",
                "Não foi possível concluir a operação. Tente novamente em instantes.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::describe)
                .toList();
        return handleExceptionInternal(exception, validationProblem(fieldErrors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> fieldErrors = exception.getAllValidationResults().stream()
                .flatMap(ApiExceptionHandler::describe)
                .toList();
        return handleExceptionInternal(exception, validationProblem(fieldErrors), headers, status, request);
    }

    private static Stream<Map<String, String>> describe(ParameterValidationResult result) {
        if (result instanceof ParameterErrors parameterErrors) {
            return parameterErrors.getFieldErrors().stream().map(ApiExceptionHandler::describe);
        }
        String parameterName = String.valueOf(result.getMethodParameter().getParameterName());
        return result.getResolvableErrors().stream()
                .map(error -> Map.of("field", parameterName, "message", String.valueOf(error.getDefaultMessage())));
    }

    private static Map<String, String> describe(FieldError fieldError) {
        return Map.of("field", fieldError.getField(), "message", String.valueOf(fieldError.getDefaultMessage()));
    }

    private ProblemDetail validationProblem(List<Map<String, String>> fieldErrors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Dados inválidos",
                "Um ou mais campos não passaram na validação");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        return problem;
    }
}
