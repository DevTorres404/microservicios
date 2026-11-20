package com.example.microservicios.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.microservicios.inventario.domain.StockItem;
import com.example.microservicios.inventario.exception.InsufficientStockException;
import com.example.microservicios.inventario.exception.InvalidReservationException;
import com.example.microservicios.inventario.exception.ReservationConflictException;
import com.example.microservicios.inventario.repository.ReservationRepository;
import com.example.microservicios.inventario.repository.StockRepository;
import com.example.microservicios.inventario.reservation.ReservationLineRequest;
import com.example.microservicios.inventario.reservation.ReservationRequest;
import com.example.microservicios.inventario.reservation.ReservationResult;
import com.example.microservicios.inventario.reservation.ReservationService;
import com.example.microservicios.inventario.reservation.ReservedLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the inventory persistence model against a real PostgreSQL 16 server.
 *
 * <p>The interesting behaviour is not "the repository saves": it is that a reservation is atomic,
 * idempotent and safe under concurrency. Those properties depend on the database, so they are
 * asserted here and not with mocks.
 */
@SpringBootTest
class InventarioRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void removeRowsFromTheSharedContainer() {
        JdbcTemplate jdbc = jdbc();
        jdbc.update("delete from reservation_lines");
        jdbc.update("delete from reservations");
        jdbc.update("delete from stock");
    }

    @Test
    void storesAndReadsStockKeepingTwoDecimalPrices() {
        stockRepository.saveAndFlush(stockItem("SKU-A", 10, "19.99"));
        stockRepository.saveAndFlush(stockItem("SKU-B", 4, "2.50"));

        StockItem found = stockRepository.findById("SKU-A").orElseThrow();

        assertThat(found.getAvailable()).isEqualTo(10);
        assertThat(found.getUnitPrice()).isEqualByComparingTo("19.99");
        assertThat(stockRepository.findAllByOrderBySkuAsc())
                .extracting(StockItem::getSku)
                .containsExactly("SKU-A", "SKU-B");
    }

    @Test
    @Transactional
    void decrementsStockOnlyWhenTheConditionHolds() {
        stockRepository.saveAndFlush(stockItem("SKU-A", 5, "10.00"));

        assertThat(stockRepository.decrementIfAvailable("SKU-A", 3)).isEqualTo(1);
        assertThat(stockRepository.decrementIfAvailable("SKU-A", 3)).isZero();
        assertThat(stockRepository.decrementIfAvailable("SKU-UNKNOWN", 1)).isZero();

        assertThat(availableOf("SKU-A")).isEqualTo(2);
    }

    @Test
    void reservesEveryLineAndSnapshotsTheUnitPrice() {
        stockRepository.saveAndFlush(stockItem("SKU-A", 10, "10.00"));
        stockRepository.saveAndFlush(stockItem("SKU-B", 5, "2.50"));

        ReservationResult result = reservationService.reserve(reservation(1001L, line("SKU-A", 2), line("SKU-B", 3)));

        assertThat(result.idempotent()).isFalse();
        assertThat(result.total().amount()).isEqualByComparingTo("27.50");
        assertThat(result.lines()).extracting(ReservedLine::sku).containsExactlyInAnyOrder("SKU-A", "SKU-B");
        assertThat(availableOf("SKU-A")).isEqualTo(8);
        assertThat(availableOf("SKU-B")).isEqualTo(2);
        assertThat(reservationRepository.findByOrderId(1001L)).isPresent();
        assertThat(rows("reservation_lines")).isEqualTo(2);
    }

    @Test
    void rejectsTheWholeReservationWhenOneLineLacksStock() {
        stockRepository.saveAndFlush(stockItem("SKU-A", 5, "10.00"));
        stockRepository.saveAndFlush(stockItem("SKU-B", 1, "2.50"));

        assertThatThrownBy(() -> reservationService.reserve(
                        reservation(1002L, line("SKU-A", 2), line("SKU-B", 2))))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(thrown -> {
                    InsufficientStockException error = (InsufficientStockException) thrown;
                    assertThat(error.missingSkus()).containsExactly("SKU-B");
                    assertThat(error.code()).isEqualTo("STOCK_INSUFICIENTE");
                });

        assertThat(availableOf("SKU-A"))
                .as("the first line was already decremented, so only a rollback keeps it intact")
                .isEqualTo(5);
        assertThat(reservationRepository.findByOrderId(1002L)).isEmpty();
    }

    @Test
    void retryingTheSameReservationDoesNotConsumeStockTwice() {
        stockRepository.saveAndFlush(stockItem("SKU-A", 10, "10.00"));

        ReservationResult first = reservationService.reserve(reservation(1003L, line("SKU-A", 2)));
        ReservationResult retry = reservationService.reserve(reservation(1003L, line("SKU-A", 2)));

        assertThat(retry.idempotent()).isTrue();
        assertThat(retry.total().amount()).isEqualByComparingTo(first.total().amount());
        assertThat(availableOf("SKU-A")).isEqualTo(8);
        assertThat(rows("reservations")).isEqualTo(1);
        assertThat(rows("reservation_lines")).isEqualTo(1);
    }

    @Test
    void rejectsARetryWithDifferentQuantities() {
        stockRepository.saveAndFlush(stockItem("SKU-A", 10, "10.00"));
        reservationService.reserve(reservation(1004L, line("SKU-A", 2)));

        assertThatThrownBy(() -> reservationService.reserve(reservation(1004L, line("SKU-A", 3))))
                .isInstanceOf(ReservationConflictException.class)
                .satisfies(thrown -> assertThat(((ReservationConflictException) thrown).code())
                        .isEqualTo("RESERVA_CONFLICTO"));

        assertThat(availableOf("SKU-A")).isEqualTo(8);
    }

    @Test
    void rejectsInvalidReservationRequests() {
        assertThatThrownBy(() -> reservationService.reserve(new ReservationRequest(1005L, List.of())))
                .isInstanceOf(InvalidReservationException.class);

        assertThatThrownBy(() -> reservationService.reserve(reservation(1005L, line("SKU-A", 0))))
                .isInstanceOf(InvalidReservationException.class);

        assertThatThrownBy(() -> reservationService.reserve(
                        reservation(1005L, line("SKU-A", 1), line("SKU-A", 2))))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void concurrentReservationsCannotOversellTheSameStock() throws Exception {
        stockRepository.saveAndFlush(stockItem("SKU-A", 1, "10.00"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> outcomes = new ArrayList<>();
            for (long orderId : new long[] {2001L, 2002L}) {
                Callable<Object> attempt = () -> {
                    bothReady.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        return reservationService.reserve(reservation(orderId, line("SKU-A", 1)));
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                };
                outcomes.add(pool.submit(attempt));
            }

            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(ReservationResult.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(InsufficientStockException.class::isInstance).hasSize(1);
            assertThat(availableOf("SKU-A")).as("never oversold").isZero();
            assertThat(rows("reservations")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void releasesStockOnceAndToleratesRepeatedOrUnknownReleases() {
        stockRepository.saveAndFlush(stockItem("SKU-A", 10, "10.00"));
        reservationService.reserve(reservation(1006L, line("SKU-A", 3)));
        assertThat(availableOf("SKU-A")).isEqualTo(7);

        assertThat(reservationService.release(1006L)).isTrue();
        assertThat(availableOf("SKU-A")).isEqualTo(10);

        assertThat(reservationService.release(1006L)).isFalse();
        assertThat(availableOf("SKU-A")).isEqualTo(10);

        assertThat(reservationService.release(999_999L)).isFalse();
        assertThat(availableOf("SKU-A")).isEqualTo(10);
    }

    @Test
    void rejectsReservingAnAlreadyReleasedOrder() {
        stockRepository.saveAndFlush(stockItem("SKU-A", 10, "10.00"));
        reservationService.reserve(reservation(1007L, line("SKU-A", 1)));
        reservationService.release(1007L);

        assertThatThrownBy(() -> reservationService.reserve(reservation(1007L, line("SKU-A", 1))))
                .isInstanceOf(ReservationConflictException.class);

        assertThat(availableOf("SKU-A")).isEqualTo(10);
    }

    private ReservationRequest reservation(long orderId, ReservationLineRequest... lines) {
        return new ReservationRequest(orderId, List.of(lines));
    }

    private ReservationLineRequest line(String sku, int quantity) {
        return new ReservationLineRequest(sku, quantity);
    }

    private StockItem stockItem(String sku, int available, String unitPrice) {
        return new StockItem(sku, available, new BigDecimal(unitPrice));
    }

    private int availableOf(String sku) {
        return jdbc().queryForObject("select available from stock where sku = ?", Integer.class, sku);
    }

    private int rows(String table) {
        return jdbc().queryForObject("select count(*) from " + table, Integer.class);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }
}
