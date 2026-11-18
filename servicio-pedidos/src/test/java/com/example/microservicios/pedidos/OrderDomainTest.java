package com.example.microservicios.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.microservicios.pedidos.domain.Money;
import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLine;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.exception.InvalidOrderException;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of the order aggregate: creation rules, the initial state and the server-side total.
 *
 * <p>The central property is structural: there is no way to hand a price or a total to the aggregate,
 * so a client-supplied amount cannot become the source of the total.
 */
class OrderDomainTest {

    @Test
    void createsAPendingOrderWithoutPricesOrTotal() {
        Order order = Order.create("Ada Lovelace",
                List.of(new OrderLineDraft("SKU-A", 2), new OrderLineDraft("SKU-B", 1)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDIENTE);
        assertThat(order.getTotal()).isEqualTo(Money.zero());
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getLines()).extracting(OrderLine::getSku)
                .containsExactlyInAnyOrder("SKU-A", "SKU-B");
        assertThat(order.getLines()).allSatisfy(line -> assertThat(line.getUnitPrice()).isNull());
    }

    @Test
    void rejectsAnOrderWithoutLines() {
        assertThatThrownBy(() -> Order.create("Ada Lovelace", List.of()))
                .isInstanceOf(InvalidOrderException.class)
                .satisfies(error -> assertThat(((InvalidOrderException) error).code())
                        .isEqualTo("PEDIDO_INVALIDO"));
    }

    @Test
    void rejectsNonPositiveQuantities() {
        assertRejectedByDomain(() -> Order.create("Ada Lovelace", List.of(new OrderLineDraft("SKU-A", 0))));
        assertRejectedByDomain(() -> Order.create("Ada Lovelace", List.of(new OrderLineDraft("SKU-A", -2))));
    }

    @Test
    void rejectsABlankCustomer() {
        assertRejectedByDomain(() -> Order.create("   ", List.of(new OrderLineDraft("SKU-A", 1))));
        assertRejectedByDomain(() -> Order.create(null, List.of(new OrderLineDraft("SKU-A", 1))));
    }

    @Test
    void rejectsDuplicatedSkusBecauseInventoryReservesOneLinePerSku() {
        assertRejectedByDomain(() -> Order.create("Ada Lovelace",
                List.of(new OrderLineDraft("SKU-A", 1), new OrderLineDraft("SKU-A", 2))));
    }

    @Test
    void computesTheTotalOnlyFromInventoryPrices() {
        Order order = Order.create("Ada Lovelace",
                List.of(new OrderLineDraft("SKU-A", 2), new OrderLineDraft("SKU-B", 3)));

        order.applyInventoryPrices(Map.of("SKU-A", Money.of("10.00"), "SKU-B", Money.of("2.50")));

        assertThat(order.getTotal().amount()).isEqualByComparingTo("27.50");
        assertThat(order.getLines()).extracting(OrderLine::getUnitPrice)
                .containsExactlyInAnyOrder(Money.of("10.00"), Money.of("2.50"));
    }

    @Test
    void rejectsInventoryPricesThatDoNotCoverEveryLine() {
        Order order = Order.create("Ada Lovelace",
                List.of(new OrderLineDraft("SKU-A", 1), new OrderLineDraft("SKU-B", 1)));

        assertRejectedByDomain(() -> order.applyInventoryPrices(Map.of("SKU-A", Money.of("10.00"))));
    }

    @Test
    void theCustomerFacingLineDraftCarriesNoPriceComponent() {
        assertThat(OrderLineDraft.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("sku", "quantity");
    }

    @Test
    void moneyNormalisesWithScaleTwoAndHalfUp() {
        assertThat(Money.of("10.005").amount()).isEqualByComparingTo("10.01");
        assertThat(Money.of("0.004").amount()).isEqualByComparingTo("0.00");
        assertThat(Money.of("2.5").amount().scale()).isEqualTo(2);
        assertThatThrownBy(() -> Money.of("-0.01")).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRejectedByDomain(ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(InvalidOrderException.class);
    }
}
