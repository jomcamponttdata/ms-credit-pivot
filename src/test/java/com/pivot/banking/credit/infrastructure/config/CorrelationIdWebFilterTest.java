package com.pivot.banking.credit.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.ContextView;

/** Unit tests for the correlation filter. */
class CorrelationIdWebFilterTest {

  @Test
  void propagatesIncomingCorrelationId() {
    // Arrange
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/credits")
                .header("X-Correlation-ID", "correlation-1")
                .build());
    AtomicReference<ContextView> context = new AtomicReference<>();
    WebFilterChain chain =
        ignored ->
            Mono.deferContextual(
                value -> {
                  context.set(value);
                  return Mono.empty();
                });

    // Act
    Mono<Void> result = new CorrelationIdWebFilter().filter(exchange, chain);

    // Assert
    StepVerifier.create(result).verifyComplete();
    assertEquals("correlation-1", exchange.getResponse().getHeaders().getFirst("X-Correlation-ID"));
    assertEquals("correlation-1", context.get().get("correlationId"));
  }

  @Test
  void generatesCorrelationIdWhenHeaderIsMissingOrBlank() {
    // Arrange
    var missing = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/credits").build());
    var blank =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/credits").header("X-Correlation-ID", " ").build());
    WebFilterChain chain = ignored -> Mono.empty();

    // Act
    var first = new CorrelationIdWebFilter().filter(missing, chain);
    var second = new CorrelationIdWebFilter().filter(blank, chain);

    // Assert
    StepVerifier.create(first).verifyComplete();
    StepVerifier.create(second).verifyComplete();
    String generated = missing.getResponse().getHeaders().getFirst("X-Correlation-ID");
    assertNotNull(generated);
    assertEquals(false, generated.isBlank());
    assertEquals(false, blank.getResponse().getHeaders().getFirst("X-Correlation-ID").isBlank());
  }

  @Test
  void classifiesRequestOutcomesAndRestoresPreviousMdcValue() {
    // Arrange
    String originalCorrelationId = MDC.get("correlationId");
    String previousCorrelationId = "outer-correlation";
    MDC.put("correlationId", previousCorrelationId);
    var clientError =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/credits")
                .header("X-Correlation-ID", "client-error-correlation")
                .build());
    clientError.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
    var serverError =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/credits")
                .header("X-Correlation-ID", "server-error-correlation")
                .build());
    serverError.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
    var failed =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/credits")
                .header("X-Correlation-ID", "failed-correlation")
                .build());
    WebFilterChain completed = ignored -> Mono.empty();

    try {
      // Act
      Mono<Void> clientErrorResult = new CorrelationIdWebFilter().filter(clientError, completed);
      Mono<Void> serverErrorResult = new CorrelationIdWebFilter().filter(serverError, completed);
      Mono<Void> failedResult =
          new CorrelationIdWebFilter()
              .filter(failed, ignored -> Mono.error(new IllegalStateException("test failure")));

      // Assert
      StepVerifier.create(clientErrorResult).verifyComplete();
      assertEquals(previousCorrelationId, MDC.get("correlationId"));
      StepVerifier.create(serverErrorResult).verifyComplete();
      assertEquals(previousCorrelationId, MDC.get("correlationId"));
      StepVerifier.create(failedResult).expectError(IllegalStateException.class).verify();
      assertEquals(previousCorrelationId, MDC.get("correlationId"));
    } finally {
      if (originalCorrelationId == null) {
        MDC.remove("correlationId");
      } else {
        MDC.put("correlationId", originalCorrelationId);
      }
    }
  }
}
