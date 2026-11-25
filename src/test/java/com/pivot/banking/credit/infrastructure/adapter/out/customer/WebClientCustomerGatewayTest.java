package com.pivot.banking.credit.infrastructure.adapter.out.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import java.net.SocketTimeoutException;
import java.util.UUID;
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
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WebClientCustomerGatewayTest {

  private static final String URL = "http://customer.test";

  @Mock private WebClient.Builder builder;

  @Test
  void retrievesActiveCustomerAndPropagatesCorrelationId() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    AtomicReference<String> header = new AtomicReference<>();
    var gateway =
        gateway(
            request -> {
              header.set(request.headers().getFirst("X-Correlation-ID"));
              return json(HttpStatus.OK, "{\"customerType\":\"PERSONAL\",\"status\":\"ACTIVE\"}");
            });

    // Act
    var result = gateway.findById(customerId, "correlation-1");

    // Assert
    result
        .test()
        .assertValue(
            customer -> {
              assertEquals(customerId, customer.id());
              assertEquals(CustomerType.PERSONAL, customer.customerType());
              assertEquals(true, customer.active());
              return true;
            })
        .assertComplete();
    assertEquals("correlation-1", header.get());
    verify(builder).baseUrl(URL);
  }

  @Test
  void mapsNotFoundAndConfirmedFailures() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var notFoundGateway =
        gateway(request -> json(HttpStatus.NOT_FOUND, "{\"title\":\"Not found\"}"));
    var serverErrorGateway =
        gateway(request -> json(HttpStatus.INTERNAL_SERVER_ERROR, "database unavailable"));

    // Act
    var notFound = notFoundGateway.findById(customerId);
    var serverError = serverErrorGateway.findById(customerId);

    // Assert
    notFound.test().assertError(ResourceNotFoundException.class);
    serverError
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && !failure.isAmbiguous());
  }

  @Test
  void mapsTransportAndResponseFailuresAsConfirmed() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var timeoutGateway =
        gateway(
            request ->
                Mono.error(
                    new WebClientRequestException(
                        new SocketTimeoutException("timeout"),
                        org.springframework.http.HttpMethod.GET,
                        request.url(),
                        request.headers())));
    var invalidGateway =
        gateway(
            request -> json(HttpStatus.OK, "{\"customerType\":\"UNKNOWN\",\"status\":\"ACTIVE\"}"));

    // Act
    var timeout = timeoutGateway.findById(customerId);
    var invalid = invalidGateway.findById(customerId);

    // Assert
    timeout
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && !failure.isAmbiguous());
    invalid
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && !failure.isAmbiguous());
  }

  @Test
  void mapsInactiveCustomerSnapshot() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var gateway =
        gateway(
            request ->
                json(HttpStatus.OK, "{\"customerType\":\"PERSONAL\",\"status\":\"INACTIVE\"}"));

    // Act
    var result = gateway.findById(customerId);

    // Assert
    result
        .test()
        .assertValue(
            customer -> {
              assertEquals(false, customer.active());
              return true;
            })
        .assertComplete();
  }

  private WebClientCustomerGateway gateway(ExchangeFunction exchangeFunction) {
    when(builder.baseUrl(URL)).thenReturn(builder);
    when(builder.build())
        .thenReturn(WebClient.builder().baseUrl(URL).exchangeFunction(exchangeFunction).build());
    ReactiveCircuitBreakerFactory circuitBreakerFactory = mock(ReactiveCircuitBreakerFactory.class);
    when(circuitBreakerFactory.create(anyString())).thenReturn(passThroughCircuitBreaker());
    return new WebClientCustomerGateway(builder, URL, circuitBreakerFactory, 30_000);
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
}
