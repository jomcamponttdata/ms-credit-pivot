package com.pivot.banking.credit.infrastructure.adapter.out.transaction;

import com.pivot.banking.credit.application.port.out.MovementGateway;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import com.pivot.banking.credit.domain.MovementType;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

/**
 * Reactive adapter for movement registration in transaction-service with circuit breaker and 2s
 * timeout.
 */
@Component
public class WebClientMovementGateway implements MovementGateway {

  private final WebClient client;
  private final Duration timeout;
  private final ReactiveCircuitBreaker circuitBreaker;

  /** Creates the client from the external URL and timeout. */
  public WebClientMovementGateway(
      WebClient.Builder builder,
      @Value("${credit.services.transaction-url}") String transactionServiceUrl,
      @Value("${credit.services.transaction-timeout-ms:2000}") long timeoutMs,
      ReactiveCircuitBreakerFactory circuitBreakerFactory) {
    this.client = builder.baseUrl(transactionServiceUrl).build();
    this.timeout = Duration.ofMillis(timeoutMs);
    this.circuitBreaker = circuitBreakerFactory.create("transactionService");
  }

  /** Registers a movement and preserves ambiguous transport outcomes. */
  @Override
  public Single<MovementReceipt> register(MovementCommand command) {
    var request =
        new MovementRequest(
            command.productId(),
            command.productType().name(),
            command.movementType().name(),
            command.amount().amount(),
            command.amount().currency(),
            command.description());
    WebClient.RequestHeadersSpec<?> specification =
        client.post().uri("/api/v1/movements").bodyValue(request);
    if (command.correlationId() != null && !command.correlationId().isBlank()) {
      specification = specification.header("X-Correlation-ID", command.correlationId());
    }
    Mono<MovementReceipt> result =
        specification
            .retrieve()
            .onStatus(
                status -> status.isError(),
                response ->
                    response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Movement service failed")
                        .map(
                            body ->
                                new DownstreamServiceException(
                                    "Transaction service returned an error: " + body,
                                    response.statusCode().is5xxServerError(),
                                    null)))
            .bodyToMono(MovementResponse.class)
            .switchIfEmpty(
                Mono.error(
                    new DownstreamServiceException(
                        "Transaction service returned no receipt", true, null)))
            .timeout(timeout)
            .map(response -> confirm(request, response))
            .onErrorMap(
                TimeoutException.class,
                failure ->
                    new DownstreamServiceException(
                        "Movement registration outcome is ambiguous", true, failure))
            .onErrorMap(
                WebClientRequestException.class,
                failure ->
                    new DownstreamServiceException(
                        "Movement registration outcome is ambiguous", true, failure))
            .onErrorMap(
                failure -> !(failure instanceof DownstreamServiceException),
                failure ->
                    new DownstreamServiceException(
                        "Movement registration response could not be confirmed", true, failure));
    return RxJava3Adapter.monoToSingle(circuitBreaker.run(result));
  }

  private MovementReceipt confirm(MovementRequest request, MovementResponse response) {
    if (response == null
        || response.id() == null
        || response.id().isBlank()
        || !request.productId().toString().equals(response.productId())
        || !request.productType().equals(response.productType())
        || !request.movementType().equals(response.movementType())
        || request.amount().compareTo(response.amount()) != 0
        || !request.currency().equals(response.currency())
        || response.createdAt() == null) {
      throw new IllegalArgumentException("Movement receipt does not match the request");
    }
    return new MovementReceipt(
        response.id(),
        MovementType.valueOf(response.movementType()),
        new Money(response.amount(), response.currency()),
        response.createdAt().toInstant());
  }

  private record MovementRequest(
      UUID productId,
      String productType,
      String movementType,
      BigDecimal amount,
      String currency,
      String description) {}

  private record MovementResponse(
      String id,
      String productId,
      String productType,
      String movementType,
      BigDecimal amount,
      String currency,
      OffsetDateTime createdAt) {}
}
