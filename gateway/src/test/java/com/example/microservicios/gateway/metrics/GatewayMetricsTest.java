package com.example.microservicios.gateway.metrics;

import com.example.microservicios.gateway.filter.LoadBalancerMetricsFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayMetricsTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final LoadBalancerMetricsFilter filter = new LoadBalancerMetricsFilter(meterRegistry);

    @Test
    @DisplayName("Increments lb_requests_total with service and instance tags when X-Service-Instance is present")
    void shouldIncrementMetricWhenServiceInstanceHeaderPresent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pedidos").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().getHeaders().set(LoadBalancerMetricsFilter.SERVICE_INSTANCE_HEADER, "pedidos-1");

        GatewayFilterChain chain = filterExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        double count = meterRegistry.counter(LoadBalancerMetricsFilter.METRIC_NAME,
                "service", "servicio-pedidos",
                "instance", "pedidos-1").count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Increments lb_requests_total for inventario service")
    void shouldIncrementMetricForInventario() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/inventario").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().getHeaders().set(LoadBalancerMetricsFilter.SERVICE_INSTANCE_HEADER, "inventario-2");

        GatewayFilterChain chain = filterExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        double count = meterRegistry.counter(LoadBalancerMetricsFilter.METRIC_NAME,
                "service", "servicio-inventario",
                "instance", "inventario-2").count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Does not increment metric when X-Service-Instance header is absent")
    void shouldNotIncrementWhenHeaderAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/pedidos").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = filterExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertThat(meterRegistry.find(LoadBalancerMetricsFilter.METRIC_NAME).counter()).isNull();
    }
}
