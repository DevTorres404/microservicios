package com.example.microservicios.gateway.web;

import com.example.microservicios.gateway.filter.CorrelationIdFilter;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Fallback endpoint invoked by Spring Cloud Gateway CircuitBreaker filter.
 * Returns an RFC 9457 ProblemDetail with HTTP 503 and a stable error code.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/{service}")
    public Mono<ResponseEntity<ProblemDetail>> fallback(@PathVariable("service") String service,
                                                        ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        String code = "inventario".equalsIgnoreCase(service) || "servicio-inventario".equalsIgnoreCase(service)
                ? "INVENTARIO_NO_DISPONIBLE"
                : ("pedidos".equalsIgnoreCase(service) || "servicio-pedidos".equalsIgnoreCase(service)
                    ? "PEDIDOS_NO_DISPONIBLE"
                    : "SERVICE_UNAVAILABLE");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The requested service (" + service + ") is currently unavailable. Please try again later."
        );
        problemDetail.setType(URI.create("https://api.example.com/errors/service-unavailable"));
        problemDetail.setTitle("Service Unavailable");
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("correlationId", correlationId != null ? correlationId : "");
        problemDetail.setProperty("timestamp", Instant.now().toString());
        problemDetail.setProperty("path", exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail));
    }
}
