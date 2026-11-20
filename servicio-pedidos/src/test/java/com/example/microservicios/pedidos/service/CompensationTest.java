package com.example.microservicios.pedidos.service;

import com.example.microservicios.pedidos.client.InventarioClient;
import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompensationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventarioClient inventarioClient;

    private SimpleMeterRegistry meterRegistry;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orderService = new OrderService(orderRepository, inventarioClient, meterRegistry);
    }

    @Test
    @DisplayName("Cancelling order triggers compensatory release on inventory")
    void shouldTriggerCompensationOnCancellation() {
        Order order = Order.create("Alice", List.of(new OrderLineDraft("SKU-1", 1)));
        order.confirm();
        given(orderRepository.findById(10L)).willReturn(Optional.of(order));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        Order cancelled = orderService.cancelOrder(10L);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELADO);
        verify(inventarioClient).release(10L);
    }

    @Test
    @DisplayName("When compensatory release fails, cancellation is not reverted and metric is incremented")
    void shouldNotRevertCancellationWhenReleaseFails() {
        Order order = Order.create("Bob", List.of(new OrderLineDraft("SKU-1", 1)));
        order.confirm();
        given(orderRepository.findById(20L)).willReturn(Optional.of(order));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        willThrow(new RuntimeException("Inventory release timeout"))
                .given(inventarioClient).release(20L);

        Order cancelled = orderService.cancelOrder(20L);

        // State must remain CANCELADO (honest state, not rolled back)
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELADO);

        // Metric must be incremented
        double failures = meterRegistry.counter("compensation_failures_total", "service", "servicio-pedidos").count();
        assertThat(failures).isEqualTo(1.0);
    }
}
