package com.example.microservicios.pedidos.domain;

import com.example.microservicios.pedidos.exception.IllegalStateTransitionException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoStateMachineTest {

    @Test
    @DisplayName("PENDIENTE -> CONFIRMADO is allowed")
    void shouldTransitionFromPendienteToConfirmado() {
        Order order = createSampleOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDIENTE);

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMADO);
    }

    @Test
    @DisplayName("PENDIENTE -> RECHAZADO is allowed")
    void shouldTransitionFromPendienteToRechazado() {
        Order order = createSampleOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDIENTE);

        order.reject();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECHAZADO);
    }

    @Test
    @DisplayName("CONFIRMADO -> CANCELADO is allowed")
    void shouldTransitionFromConfirmadoToCancelado() {
        Order order = createSampleOrder();
        order.confirm();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMADO);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELADO);
    }

    @Test
    @DisplayName("PENDIENTE -> CANCELADO is allowed")
    void shouldTransitionFromPendienteToCancelado() {
        Order order = createSampleOrder();
        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELADO);
    }

    @Test
    @DisplayName("CANCELADO -> CONFIRMADO is forbidden")
    void shouldRejectTransitionFromCanceladoToConfirmado() {
        Order order = createSampleOrder();
        order.confirm();
        order.cancel();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("Cannot transition from CANCELADO to CONFIRMADO");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELADO);
    }

    @Test
    @DisplayName("CANCELADO -> CANCELADO is forbidden")
    void shouldRejectTransitionFromCanceladoToCancelado() {
        Order order = createSampleOrder();
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("Cannot transition from CANCELADO to CANCELADO");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELADO);
    }

    @Test
    @DisplayName("RECHAZADO -> CONFIRMADO is forbidden")
    void shouldRejectTransitionFromRechazadoToConfirmado() {
        Order order = createSampleOrder();
        order.reject();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("Cannot transition from RECHAZADO to CONFIRMADO");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECHAZADO);
    }

    @Test
    @DisplayName("RECHAZADO -> CANCELADO is forbidden")
    void shouldRejectTransitionFromRechazadoToCancelado() {
        Order order = createSampleOrder();
        order.reject();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("Cannot transition from RECHAZADO to CANCELADO");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECHAZADO);
    }

    @Test
    @DisplayName("CONFIRMADO -> RECHAZADO is forbidden")
    void shouldRejectTransitionFromConfirmadoToRechazado() {
        Order order = createSampleOrder();
        order.confirm();

        assertThatThrownBy(order::reject)
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("Cannot transition from CONFIRMADO to RECHAZADO");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMADO);
    }

    @Test
    @DisplayName("Total is calculated exclusively with inventory prices")
    void shouldCalculateTotalWithInventoryPrices() {
        Order order = Order.create("Alice", List.of(
                new OrderLineDraft("SKU-1", 2),
                new OrderLineDraft("SKU-2", 3)
        ));
        assertThat(order.getTotal()).isEqualTo(Money.zero());

        order.applyInventoryPrices(Map.of(
                "SKU-1", Money.of("10.50"),
                "SKU-2", Money.of("5.00")
        ));

        // 2 * 10.50 + 3 * 5.00 = 21.00 + 15.00 = 36.00
        assertThat(order.getTotal()).isEqualTo(Money.of("36.00"));
        assertThat(order.getLines().get(0).getUnitPrice()).isEqualTo(Money.of("10.50"));
        assertThat(order.getLines().get(1).getUnitPrice()).isEqualTo(Money.of("5.00"));
    }

    private Order createSampleOrder() {
        return Order.create("Customer A", List.of(new OrderLineDraft("SKU-1", 1)));
    }
}
