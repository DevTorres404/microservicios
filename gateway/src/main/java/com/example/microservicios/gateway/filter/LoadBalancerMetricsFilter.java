package com.example.microservicios.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * GlobalFilter that inspects the downstream response for the X-Service-Instance header
 * and records the metric lb_requests_total{service,instance} in Micrometer/Prometheus.
 */
@Component
public class LoadBalancerMetricsFilter implements GlobalFilter, Ordered {

    public static final String SERVICE_INSTANCE_HEADER = "X-Service-Instance";
    public static final String METRIC_NAME = "lb_requests_total";

    private final MeterRegistry meterRegistry;

    public LoadBalancerMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doFinally(signalType -> recordMetrics(exchange));
    }

    public void recordMetrics(ServerWebExchange exchange) {
        String instance = exchange.getResponse().getHeaders().getFirst(SERVICE_INSTANCE_HEADER);
        if (instance != null && !instance.isBlank()) {
            String path = exchange.getRequest().getPath().value();
            String service = resolveServiceName(path, instance);
            meterRegistry.counter(METRIC_NAME, "service", service, "instance", instance).increment();
        }
    }

    private String resolveServiceName(String path, String instance) {
        if (path.startsWith("/api/pedidos") || instance.contains("pedidos")) {
            return "servicio-pedidos";
        }
        if (path.startsWith("/api/inventario") || instance.contains("inventario")) {
            return "servicio-inventario";
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
