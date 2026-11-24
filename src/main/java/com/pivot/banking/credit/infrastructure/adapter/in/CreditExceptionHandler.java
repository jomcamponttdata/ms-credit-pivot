package com.pivot.banking.credit.infrastructure.adapter.in;

import com.pivot.banking.credit.adapter.in.dto.Problem;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import com.pivot.banking.credit.domain.exception.ConcurrentModificationException;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.stream.Collectors;

/** Maps application and framework errors to RFC 7807 problem responses. */
@RestControllerAdvice
public class CreditExceptionHandler {

    /** Returns 404 when a resource or referenced customer does not exist. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Problem> notFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    /** Returns 409 for concurrent modifications. */
    @ExceptionHandler(ConcurrentModificationException.class)
    public ResponseEntity<Problem> conflict(ConcurrentModificationException exception) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", exception.getMessage());
    }

    /** Returns 422 when a business rule is violated. */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Problem> businessRule(BusinessRuleViolationException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", exception.getMessage());
    }

    /** Returns 503 for downstream failures, adding reconciliation notice when ambiguous. */
    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<Problem> downstream(DownstreamServiceException exception) {
        String detail = exception.isAmbiguous()
                ? exception.getMessage() + "; operation outcome is ambiguous and requires reconciliation"
                : exception.getMessage();
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Dependent service unavailable", detail);
    }

    /** Returns 400 for invalid request values. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Problem> badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    /** Returns 400 with field details for body validation failures. */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Problem> requestBodyValidation(WebExchangeBindException exception) {
        String detail = exception.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    /** Returns 400 for method parameter validation failures. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Problem> parameterValidation(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request parameters are invalid");
    }

    /** Returns 400 for malformed or unreadable HTTP input. */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Problem> unreadableInput(ServerWebInputException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getReason());
    }

    /** Returns 503 when the relational database is unavailable. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Problem> persistenceUnavailable(DataAccessException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Credit storage unavailable",
                "The credit operation could not be persisted at this time");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String title, String detail) {
        Problem body = new Problem(title, status.value()).type("about:blank").detail(detail);
        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(body);
    }
}
