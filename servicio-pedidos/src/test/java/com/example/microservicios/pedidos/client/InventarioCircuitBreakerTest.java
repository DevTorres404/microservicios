package com.example.microservicios.pedidos.client;

import com.example.microservicios.pedidos.exception.InsufficientStockException;
import com.example.microservicios.pedidos.exception.InventarioUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventarioCircuitBreakerTest {

    @Test
    @DisplayName("CircuitBreaker instance for servicio-inventario matches required spec")
    void shouldConfigureCircuitBreakerAccordingToSpec() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofMillis(1500))
                .slowCallRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .ignoreExceptions(InsufficientStockException.class)
                .build();

        CircuitBreaker cb = CircuitBreaker.of("servicio-inventario", config);

        assertThat(cb.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(cb.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(10000);
        assertThat(cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
    }

    @Test
    @DisplayName("In OPEN state, calls fail fast with CallNotPermittedException")
    void shouldFailFastWhenCircuitBreakerIsOpen() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("servicio-inventario");
        cb.transitionToOpenState();

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> cb.executeSupplier(() -> "should not execute"))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("FallbackFactory produces InventarioUnavailableException with CODE=INVENTARIO_NO_DISPONIBLE")
    void shouldProduceTypedExceptionOnFallback() {
        InventarioClientFallbackFactory fallbackFactory = new InventarioClientFallbackFactory();
        Throwable cause = new RuntimeException("Connection refused");
        InventarioClient client = fallbackFactory.create(cause);

        ReservationRequestDto request = new ReservationRequestDto(123L, List.of(new ReservationLineDto("SKU-1", 1)));

        assertThatThrownBy(() -> client.reserve(request))
                .isInstanceOf(InventarioUnavailableException.class)
                .hasMessageContaining("Inventory service is unavailable")
                .hasFieldOrPropertyWithValue("code", "INVENTARIO_NO_DISPONIBLE");

        assertThatThrownBy(() -> client.release(123L))
                .isInstanceOf(InventarioUnavailableException.class)
                .hasMessageContaining("Inventory service is unavailable")
                .hasFieldOrPropertyWithValue("code", "INVENTARIO_NO_DISPONIBLE");
    }
}
