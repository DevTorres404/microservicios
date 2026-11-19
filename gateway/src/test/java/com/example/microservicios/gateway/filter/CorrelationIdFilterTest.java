package com.example.microservicios.gateway.filter;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("Reuses incoming X-Correlation-Id and propagates downstream and on response")
    void shouldReuseCorrelationIdWhenPresent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pedidos")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> downstreamHeader = new AtomicReference<>();
        GatewayFilterChain chain = filterExchange -> {
            downstreamHeader.set(filterExchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(downstreamHeader.get()).isEqualTo("abc-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Generates new UUID when X-Correlation-Id is absent")
    void shouldGenerateCorrelationIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pedidos").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> downstreamHeader = new AtomicReference<>();
        GatewayFilterChain chain = filterExchange -> {
            downstreamHeader.set(filterExchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String generated = downstreamHeader.get();
        assertThat(generated)
                .isNotNull()
                .matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo(generated);
    }

    @Test
    @DisplayName("Generates new UUID when incoming X-Correlation-Id is blank")
    void shouldGenerateCorrelationIdWhenBlank() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pedidos")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> downstreamHeader = new AtomicReference<>();
        GatewayFilterChain chain = filterExchange -> {
            downstreamHeader.set(filterExchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        String generated = downstreamHeader.get();
        assertThat(generated)
                .isNotNull()
                .matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo(generated);
    }
}
