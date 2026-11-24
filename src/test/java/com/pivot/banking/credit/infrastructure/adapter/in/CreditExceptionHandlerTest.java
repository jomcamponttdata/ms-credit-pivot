package com.pivot.banking.credit.infrastructure.adapter.in;

import com.pivot.banking.credit.adapter.in.dto.Problem;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import com.pivot.banking.credit.domain.exception.ConcurrentModificationException;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebInputException;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for RFC 7807 problem mapping. */
class CreditExceptionHandlerTest {

    private final CreditExceptionHandler handler = new CreditExceptionHandler();

    @Test
    void mapsDomainExceptions() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        ResponseEntity<Problem> notFound = handler.notFound(new ResourceNotFoundException("missing"));
        ResponseEntity<Problem> conflict = handler.conflict(new ConcurrentModificationException(id));
        ResponseEntity<Problem> business = handler.businessRule(new BusinessRuleViolationException("rule"));
        ResponseEntity<Problem> badRequest = handler.badRequest(new IllegalArgumentException("invalid"));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals(MediaType.valueOf("application/problem+json"), notFound.getHeaders().getContentType());
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("Business rule violated", business.getBody().getTitle());
        assertEquals("invalid", badRequest.getBody().getDetail());
    }

    @Test
    void mapsDownstreamValidationAndPersistenceFailures() {
        // Arrange
        DataAccessException database = new DataAccessException("unavailable") {
        };

        // Act
        ResponseEntity<Problem> ambiguous = handler.downstream(new DownstreamServiceException("timeout", true, null));
        ResponseEntity<Problem> confirmed = handler.downstream(new DownstreamServiceException("rejected", false, null));
        ResponseEntity<Problem> parameters = handler.parameterValidation(
                new ConstraintViolationException("invalid", Set.of()));
        ResponseEntity<Problem> unreadable = handler.unreadableInput(new ServerWebInputException("bad json"));
        ResponseEntity<Problem> persistence = handler.persistenceUnavailable(database);

        // Assert
        assertEquals("timeout; operation outcome is ambiguous and requires reconciliation",
                ambiguous.getBody().getDetail());
        assertEquals("rejected", confirmed.getBody().getDetail());
        assertEquals("Request parameters are invalid", parameters.getBody().getDetail());
        assertEquals("bad json", unreadable.getBody().getDetail());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, persistence.getStatusCode());
    }
}
