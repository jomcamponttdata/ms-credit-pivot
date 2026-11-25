package com.pivot.banking.credit.infrastructure.adapter.out.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pivot.banking.credit.application.port.out.MovementGateway.MovementCommand;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementType;
import com.pivot.banking.credit.domain.ProductType;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WebClientMovementGatewayTest {

  private static final String URL = "http://transaction.test";

  @Mock private WebClient.Builder builder;

  @Test
  void registersMovementAndReturnsReceipt() {
    // Arrange
    var productId = UUID.randomUUID();
    AtomicReference<String> correlation = new AtomicReference<>();
    var gateway =
        gateway(
            request -> {
              correlation.set(request.headers().getFirst("X-Correlation-ID"));
              return json(HttpStatus.CREATED, validResponse(productId));
            });

    // Act
    var result = gateway.register(command(productId, "correlation-1"));

    // Assert
    result
        .test()
        .assertValue(
            receipt -> {
              assertEquals("movement-1", receipt.movementId());
              assertEquals(MovementType.PAYMENT, receipt.movementType());
              return true;
            })
        .assertComplete();
    assertEquals("correlation-1", correlation.get());
  }

  @Test
  void mapsConfirmedAndAmbiguousFailures() {
    // Arrange
    var productId = UUID.randomUUID();
    var confirmedGateway = gateway(request -> json(HttpStatus.BAD_REQUEST, "invalid"));
    var ambiguousGateway =
        gateway(request -> json(HttpStatus.INTERNAL_SERVER_ERROR, "unavailable"));

    // Act
    var confirmed = confirmedGateway.register(command(productId));
    var ambiguous = ambiguousGateway.register(command(productId));

    // Assert
    confirmed
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && !failure.isAmbiguous());
    ambiguous
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && failure.isAmbiguous());
  }

  @Test
  void boundsStalledRequestAndRejectsMismatchedReceipt() {
    // Arrange
    var productId = UUID.randomUUID();
    var timeoutGateway = gateway(request -> Mono.never(), 50);
    var mismatchGateway =
        gateway(request -> json(HttpStatus.CREATED, validResponse(UUID.randomUUID())));

    // Act
    var timeout = timeoutGateway.register(command(productId));
    var mismatch = mismatchGateway.register(command(productId));

    // Assert
    timeout
        .test()
        .awaitDone(2, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof DownstreamServiceException failure
                    && failure.isAmbiguous()
                    && failure.getCause() instanceof TimeoutException);
    mismatch
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && failure.isAmbiguous());
  }

  @Test
  void rejectsEveryInvalidReceiptField() {
    // Arrange
    UUID productId = UUID.randomUUID();
    var bodies =
        java.util.List.of(
            """
{"id":null,"productId":"%s","productType":"CREDIT","movementType":"PAYMENT","amount":100.00,"currency":"PEN","createdAt":"2026-01-05T10:15:30Z"}
"""
                .formatted(productId),
            """
{"id":"","productId":"%s","productType":"CREDIT","movementType":"PAYMENT","amount":100.00,"currency":"PEN","createdAt":"2026-01-05T10:15:30Z"}
"""
                .formatted(productId),
            """
{"id":"movement-1","productId":"%s","productType":"CREDIT_CARD","movementType":"PAYMENT","amount":100.00,"currency":"PEN","createdAt":"2026-01-05T10:15:30Z"}
"""
                .formatted(productId),
            """
{"id":"movement-1","productId":"%s","productType":"CREDIT","movementType":"CHARGE","amount":100.00,"currency":"PEN","createdAt":"2026-01-05T10:15:30Z"}
"""
                .formatted(productId),
            """
{"id":"movement-1","productId":"%s","productType":"CREDIT","movementType":"PAYMENT","amount":100.01,"currency":"PEN","createdAt":"2026-01-05T10:15:30Z"}
"""
                .formatted(productId),
            """
{"id":"movement-1","productId":"%s","productType":"CREDIT","movementType":"PAYMENT","amount":100.00,"currency":"USD","createdAt":"2026-01-05T10:15:30Z"}
"""
                .formatted(productId),
            """
{"id":"movement-1","productId":"%s","productType":"CREDIT","movementType":"PAYMENT","amount":100.00,"currency":"PEN","createdAt":null}
"""
                .formatted(productId));

    // Act
    var results =
        bodies.stream()
            .map(
                body ->
                    gateway(request -> json(HttpStatus.CREATED, body)).register(command(productId)))
            .toList();

    // Assert
    for (var result : results) {
      result
          .test()
          .assertError(
              error ->
                  error instanceof DownstreamServiceException failure && failure.isAmbiguous());
    }
  }

  @Test
  void omitsBlankCorrelationId() {
    // Arrange
    UUID productId = UUID.randomUUID();
    AtomicReference<String> header = new AtomicReference<>();
    var gateway =
        gateway(
            request -> {
              header.set(request.headers().getFirst("X-Correlation-ID"));
              return json(HttpStatus.CREATED, validResponse(productId));
            });

    // Act
    var result = gateway.register(command(productId, " "));

    // Assert
    result.test().assertValueCount(1).assertComplete();
    assertEquals(null, header.get());
  }

  private MovementCommand command(UUID productId) {
    return command(productId, null);
  }

  private MovementCommand command(UUID productId, String correlationId) {
    return new MovementCommand(
        productId,
        ProductType.CREDIT,
        MovementType.PAYMENT,
        new Money(new BigDecimal("100.00"), "PEN"),
        "Payment",
        correlationId);
  }

  private WebClientMovementGateway gateway(ExchangeFunction exchangeFunction) {
    return gateway(exchangeFunction, 3000);
  }

  private WebClientMovementGateway gateway(ExchangeFunction exchangeFunction, long timeoutMs) {
    when(builder.baseUrl(URL)).thenReturn(builder);
    when(builder.build())
        .thenReturn(WebClient.builder().baseUrl(URL).exchangeFunction(exchangeFunction).build());
    ReactiveCircuitBreakerFactory circuitBreakerFactory = mock(ReactiveCircuitBreakerFactory.class);
    when(circuitBreakerFactory.create(anyString())).thenReturn(passThroughCircuitBreaker());
    return new WebClientMovementGateway(builder, URL, timeoutMs, circuitBreakerFactory);
  }

  private ReactiveCircuitBreaker passThroughCircuitBreaker() {
    return new ReactiveCircuitBreaker() {
      @Override
      public <T> Mono<T> run(Mono<T> toRun, Function<Throwable, Mono<T>> fallback) {
        return toRun;
      }

      @Override
      public <T> Flux<T> run(Flux<T> toRun, Function<Throwable, Flux<T>> fallback) {
        return toRun;
      }
    };
  }

  private Mono<ClientResponse> json(HttpStatus status, String body) {
    return Mono.just(
        ClientResponse.create(status)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build());
  }

  private String validResponse(UUID productId) {
    return """
           {"id":"movement-1","productId":"%s","productType":"CREDIT","movementType":"PAYMENT",
            "amount":100.00,"currency":"PEN","createdAt":"2026-01-05T10:15:30Z"}
           """
        .formatted(productId);
  }
}
