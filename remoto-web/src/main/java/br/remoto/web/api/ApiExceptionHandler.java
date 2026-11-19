package br.remoto.web.api;

import br.remoto.storage.StaleWriteException;
import br.remoto.web.security.LoginRequiredException;
import br.remoto.web.sim.InvalidScenarioException;
import br.remoto.web.sim.QuotaExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/** Erros da API no formato RFC 9457 (application/problem+json). */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
class ApiExceptionHandler {

    @ExceptionHandler(InvalidScenarioException.class)
    ProblemDetail invalid(InvalidScenarioException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "cenário inválido");
        problem.setProperty("erros", e.errors());
        return problem;
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(StaleWriteException.class)
    ProblemDetail stale(StaleWriteException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, e.getMessage());
    }

    @ExceptionHandler(QuotaExceededException.class)
    ProblemDetail quota(QuotaExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(LoginRequiredException.class)
    ProblemDetail login(LoginRequiredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
