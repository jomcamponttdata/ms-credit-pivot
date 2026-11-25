package com.pivot.banking.credit.infrastructure.adapter.out.customer;

import com.pivot.banking.credit.application.port.out.CustomerGateway;
import com.pivot.banking.credit.domain.CustomerSnapshot;
import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import io.reactivex.rxjava3.core.Single;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

/** Reactive adapter for customer-service with circuit breaker and 2s timeout. */
@Component
public class WebClientCustomerGateway implements CustomerGateway {

  private final WebClient client;
  private final ReactiveCircuitBreaker circuitBreaker;
  private final Duration timeout;

  /** Creates the client from the externally configured customer-service URL. */
  public WebClientCustomerGateway(
      WebClient.Builder builder,
      @Value("${credit.services.customer-url}") String customerServiceUrl,
      ReactiveCircuitBreakerFactory circuitBreakerFactory,
      @Value("${credit.services.customer-timeout-ms:2000}") long timeoutMs) {
    this.client = builder.baseUrl(customerServiceUrl).build();
    this.circuitBreaker = circuitBreakerFactory.create("customerService");
    this.timeout = Duration.ofMillis(timeoutMs);
  }

  /** Retrieves the customer type and lifecycle state. */
  @Override
  public Single<CustomerSnapshot> findById(UUID customerId, String correlationId) {
    WebClient.RequestHeadersSpec<?> specification =
        client.get().uri("/api/v1/customers/{customerId}", customerId);
    if (correlationId != null && !correlationId.isBlank()) {
      specification = specification.header("X-Correlation-ID", correlationId);
    }
    Mono<CustomerSnapshot> result =
        specification
            .retrieve()
            .onStatus(
                status -> status.value() == 404,
                response ->
                    Mono.error(new ResourceNotFoundException("Customer not found: " + customerId)))
            .onStatus(
                status -> status.isError(),
                response ->
                    response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Customer service failed")
                        .map(
                            body ->
                                new DownstreamServiceException(
                                    "Customer service returned an error: " + body, false, null)))
            .bodyToMono(CustomerResponse.class)
            .switchIfEmpty(
                Mono.error(
                    new DownstreamServiceException(
                        "Customer service returned an empty response", false, null)))
            .map(
                response ->
                    new CustomerSnapshot(
                        customerId,
                        CustomerType.valueOf(response.customerType()),
                        "ACTIVE".equalsIgnoreCase(response.status())))
            .onErrorMap(
                WebClientRequestException.class,
                failure ->
                    new DownstreamServiceException(
                        "Customer service is unavailable", false, failure))
            .onErrorMap(
                failure ->
                    !(failure instanceof ResourceNotFoundException)
                        && !(failure instanceof DownstreamServiceException),
                failure ->
                    new DownstreamServiceException(
                        "Customer service returned an invalid response", false, failure))
            .timeout(timeout);
    return RxJava3Adapter.monoToSingle(circuitBreaker.run(result));
  }

  private record CustomerResponse(String customerType, String status) {}
}
