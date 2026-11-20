package com.example.microservicios.pedidos.service;

import com.example.microservicios.pedidos.client.InventarioClient;
import com.example.microservicios.pedidos.client.ReservationRequestDto;
import com.example.microservicios.pedidos.client.ReservationResponseDto;
import com.example.microservicios.pedidos.client.ReservedLineDto;
import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventarioClient inventarioClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, inventarioClient, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Idempotent reservation response is correctly processed into a CONFIRMADO order")
    void shouldHandleIdempotentReservationResponse() {
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        ReservationResponseDto idempotentResponse = new ReservationResponseDto(
                99L,
                List.of(new ReservedLineDto("SKU-1", 3, new BigDecimal("12.00"))),
                new BigDecimal("36.00"),
                true
        );
        given(inventarioClient.reserve(any(ReservationRequestDto.class))).willReturn(idempotentResponse);

        Order order = orderService.createOrder("Alice", List.of(new OrderLineDraft("SKU-1", 3)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMADO);
        assertThat(order.getTotal().amount()).isEqualByComparingTo("36.00");
    }
}
