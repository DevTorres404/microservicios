package com.example.microservicios.pedidos.service;

import com.example.microservicios.pedidos.client.InventarioClient;
import com.example.microservicios.pedidos.client.ReservationRequestDto;
import com.example.microservicios.pedidos.client.ReservationResponseDto;
import com.example.microservicios.pedidos.client.ReservedLineDto;
import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.exception.InsufficientStockException;
import com.example.microservicios.pedidos.exception.InventarioUnavailableException;
import com.example.microservicios.pedidos.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PedidoCreationTest {

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
    @DisplayName("When inventory reservation succeeds, order is confirmed with inventory prices")
    void shouldConfirmOrderWhenReservationSucceeds() {
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> {
            Order o = inv.getArgument(0);
            setOrderId(o, 1L);
            return o;
        });

        ReservationResponseDto reservationResponse = new ReservationResponseDto(
                1L,
                List.of(new ReservedLineDto("SKU-1", 2, new BigDecimal("25.00"))),
                new BigDecimal("50.00"),
                false
        );
        given(inventarioClient.reserve(any(ReservationRequestDto.class))).willReturn(reservationResponse);

        Order result = orderService.createOrder("Alice", List.of(new OrderLineDraft("SKU-1", 2)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMADO);
        assertThat(result.getTotal().amount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("When inventory reports insufficient stock (409), order is rejected")
    void shouldRejectOrderWhenStockInsufficient() {
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> {
            Order o = inv.getArgument(0);
            setOrderId(o, 2L);
            return o;
        });

        given(inventarioClient.reserve(any(ReservationRequestDto.class)))
                .willThrow(new InsufficientStockException(List.of("SKU-1")));

        assertThatThrownBy(() -> orderService.createOrder("Bob", List.of(new OrderLineDraft("SKU-1", 10))))
                .isInstanceOf(InsufficientStockException.class)
                .hasFieldOrPropertyWithValue("missingSkus", List.of("SKU-1"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.RECHAZADO);
    }

    @Test
    @DisplayName("When inventory is unavailable, order remains PENDIENTE and compensation is attempted")
    void shouldRemainPendienteAndAttemptCompensationWhenInventoryUnavailable() {
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> {
            Order o = inv.getArgument(0);
            setOrderId(o, 3L);
            return o;
        });

        given(inventarioClient.reserve(any(ReservationRequestDto.class)))
                .willThrow(new InventarioUnavailableException("Circuit breaker OPEN"));

        assertThatThrownBy(() -> orderService.createOrder("Charlie", List.of(new OrderLineDraft("SKU-1", 1))))
                .isInstanceOf(InventarioUnavailableException.class);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDIENTE);
        verify(inventarioClient).release(3L);
    }

    private void setOrderId(Order order, Long id) {
        try {
            Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
