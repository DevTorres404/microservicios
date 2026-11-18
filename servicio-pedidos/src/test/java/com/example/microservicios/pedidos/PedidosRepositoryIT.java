package com.example.microservicios.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.microservicios.pedidos.domain.Money;
import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLine;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.exception.DomainException;
import com.example.microservicios.pedidos.exception.InvalidOrderException;
import com.example.microservicios.pedidos.repository.OrderRepository;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises the order persistence model against a real PostgreSQL 16 server.
 *
 * <p>The order schema is the only durable record of a purchase, so the assertions cover the aggregate
 * round-trip, the status vocabulary enforced by the database and the ownership of lines through the
 * foreign key.
 */
@SpringBootTest
class PedidosRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void removeRowsFromTheSharedContainer() {
        JdbcTemplate jdbc = jdbc();
        jdbc.update("delete from order_lines");
        jdbc.update("delete from orders");
    }

    @Test
    void storesAndReloadsAnOrderWithItsLinesAndTotal() {
        Order order = Order.create("Ada Lovelace",
                List.of(new OrderLineDraft("SKU-A", 2), new OrderLineDraft("SKU-B", 3)));
        order.applyInventoryPrices(Map.of("SKU-A", Money.of("10.00"), "SKU-B", Money.of("2.50")));

        Long orderId = orderRepository.saveAndFlush(order).getId();
        Order reloaded = orderRepository.findById(orderId).orElseThrow();

        assertThat(reloaded.getCustomer()).isEqualTo("Ada Lovelace");
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDIENTE);
        assertThat(reloaded.getTotal().amount()).isEqualByComparingTo("27.50");
        assertThat(reloaded.getLines()).extracting(OrderLine::getSku)
                .containsExactlyInAnyOrder("SKU-A", "SKU-B");
        assertThat(reloaded.getLines()).extracting(OrderLine::getQuantity)
                .containsExactlyInAnyOrder(2, 3);
        assertThat(reloaded.getLines()).extracting(OrderLine::getUnitPrice)
                .containsExactlyInAnyOrder(Money.of("10.00"), Money.of("2.50"));
        assertThat(jdbc().queryForObject("select status from orders where id = ?", String.class, orderId))
                .as("the persisted vocabulary is the one fixed by the backend contract")
                .isEqualTo("PENDIENTE");
    }

    @Test
    void keepsOrderLinesWithoutPriceUntilInventoryConfirmsThem() {
        Order order = Order.create("Grace Hopper", List.of(new OrderLineDraft("SKU-C", 1)));

        Long orderId = orderRepository.saveAndFlush(order).getId();
        Order reloaded = orderRepository.findById(orderId).orElseThrow();

        assertThat(reloaded.getTotal()).isEqualTo(Money.zero());
        assertThat(reloaded.getLines()).singleElement()
                .satisfies(line -> assertThat(line.getUnitPrice()).isNull());
        assertThat(jdbc().queryForObject(
                "select count(*) from order_lines where order_id = ? and unit_price is null",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void findsOrdersByStatusWithTheirLinesInitialisedNewestFirst() {
        Long older = orderRepository.saveAndFlush(Order.create("Older Customer",
                List.of(new OrderLineDraft("SKU-A", 1)))).getId();
        Long newer = orderRepository.saveAndFlush(Order.create("Newer Customer",
                List.of(new OrderLineDraft("SKU-B", 2)))).getId();
        jdbc().update("update orders set created_at = now() - interval '1 hour' where id = ?", older);
        jdbc().update("insert into orders (customer, created_at, status, total) "
                + "values ('Already Confirmed', now(), 'CONFIRMADO', 0.00)");

        List<Order> pending = orderRepository.findByStatusWithLines(OrderStatus.PENDIENTE);

        assertThat(pending).extracting(Order::getId).containsExactly(newer, older);
        assertThat(pending).allSatisfy(order ->
                assertThat(order.getLines()).as("lines must be fetched, not lazy").isNotEmpty());
    }

    @Test
    void findAllWithLinesReturnsEveryOrderAndFetchesTheLines() {
        orderRepository.saveAndFlush(Order.create("First", List.of(new OrderLineDraft("SKU-A", 1))));
        orderRepository.saveAndFlush(Order.create("Second", List.of(new OrderLineDraft("SKU-B", 1))));

        List<Order> orders = orderRepository.findAllWithLines();

        assertThat(orders).hasSize(2);
        assertThat(orders).allSatisfy(order -> assertThat(order.getLines()).hasSize(1));
    }

    @Test
    void rejectsAStatusOutsideTheContractVocabulary() {
        assertThatThrownBy(() -> jdbc().update("insert into orders (customer, created_at, status, total) "
                + "values ('Anyone', now(), 'ENTREGADO', 0.00)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeTotalsAtTheDatabaseLevel() {
        Long orderId = orderRepository.saveAndFlush(
                Order.create("Someone", List.of(new OrderLineDraft("SKU-A", 1)))).getId();

        assertThatThrownBy(() -> jdbc().update("update orders set total = -1.00 where id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removesTheLinesTogetherWithTheirOrder() {
        Long orderId = orderRepository.saveAndFlush(
                Order.create("Temporary", List.of(new OrderLineDraft("SKU-A", 2)))).getId();

        orderRepository.deleteById(orderId);
        orderRepository.flush();

        assertThat(jdbc().queryForObject(
                "select count(*) from order_lines where order_id = ?", Integer.class, orderId)).isZero();
    }

    @Test
    void exposesTypedDomainErrorsWithoutHttpConcerns() {
        DomainException failure = new InvalidOrderException("no lines");

        assertThat(failure.code()).isEqualTo("PEDIDO_INVALIDO");
        assertThat(failure).isInstanceOf(RuntimeException.class);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }
}
