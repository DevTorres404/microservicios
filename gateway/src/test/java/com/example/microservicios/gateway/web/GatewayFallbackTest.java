package com.example.microservicios.gateway.web;

import com.example.microservicios.gateway.filter.CorrelationIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(controllers = FallbackController.class)
class GatewayFallbackTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Returns 503 ProblemDetail for pedidos fallback with correlation id and stable code")
    void shouldReturn503ForPedidosFallback() {
        webTestClient.get()
                .uri("/fallback/pedidos")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-pedidos-123")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("Service Unavailable")
                .jsonPath("$.code").isEqualTo("PEDIDOS_NO_DISPONIBLE")
                .jsonPath("$.correlationId").isEqualTo("corr-pedidos-123");
    }

    @Test
    @DisplayName("Returns 503 ProblemDetail for inventario fallback with correlation id and stable code")
    void shouldReturn503ForInventarioFallback() {
        webTestClient.get()
                .uri("/fallback/inventario")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-inventario-456")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("Service Unavailable")
                .jsonPath("$.code").isEqualTo("INVENTARIO_NO_DISPONIBLE")
                .jsonPath("$.correlationId").isEqualTo("corr-inventario-456");
    }
}
