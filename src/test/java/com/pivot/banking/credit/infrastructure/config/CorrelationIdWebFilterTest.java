package com.pivot.banking.credit.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.ContextView;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Unit tests for the correlation filter. */
class CorrelationIdWebFilterTest {

    @Test
    void propagatesIncomingCorrelationId() {
        // Arrange
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/credits")
                .header("X-Correlation-ID", "correlation-1").build());
        AtomicReference<ContextView> context = new AtomicReference<>();
        WebFilterChain chain = ignored -> Mono.deferContextual(value -> {
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
        var blank = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/credits")
                .header("X-Correlation-ID", " ").build());
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
}
