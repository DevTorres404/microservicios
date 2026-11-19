package com.example.microservicios.pedidos.client;

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeignTimeoutTest {

    private final FeignClientConfiguration config = new FeignClientConfiguration();

    @Test
    @DisplayName("Feign options configure 500ms connect and 1500ms read timeout")
    void shouldConfigureExpectedTimeouts() {
        Request.Options options = config.requestOptions();

        assertThat(options.connectTimeout()).isEqualTo(500);
        assertThat(options.connectTimeoutUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(options.readTimeout()).isEqualTo(1500);
        assertThat(options.readTimeoutUnit()).isEqualTo(TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("Retryer performs exactly 1 retry on connection error")
    void shouldRetryOnceOnConnectionError() {
        Retryer retryer = config.retryer();
        Request request = Request.create(Request.HttpMethod.POST, "/api/inventario/reservas",
                Map.of(), Request.Body.empty(), new RequestTemplate());
        RetryableException error = new RetryableException(503, "Connection refused",
                Request.HttpMethod.POST, new Date(), request);

        // First failure: retry allowed (attempt 1)
        retryer.continueOrPropagate(error);

        // Second failure: attempt 2 reaches maxAttempts (2) -> throws RetryableException
        assertThatThrownBy(() -> retryer.continueOrPropagate(error))
                .isSameAs(error);
    }

    @Test
    @DisplayName("Error decoder does not retry business errors (409)")
    void shouldNotRetryBusinessErrors() {
        InventarioErrorDecoder decoder = new InventarioErrorDecoder();
        Request request = Request.create(Request.HttpMethod.POST, "/api/inventario/reservas",
                Map.of(), Request.Body.empty(), new RequestTemplate());

        String jsonBody = "{\"missingSkus\":[\"SKU-1\"]}";
        Response response = Response.builder()
                .status(409)
                .reason("Conflict")
                .request(request)
                .headers(Map.of("Content-Type", java.util.List.of("application/json")))
                .body(jsonBody, java.nio.charset.StandardCharsets.UTF_8)
                .build();

        Exception exception = decoder.decode("InventarioClient#reserve", response);

        assertThat(exception)
                .isInstanceOf(com.example.microservicios.pedidos.exception.InsufficientStockException.class)
                .isNotInstanceOf(RetryableException.class);
    }
}
