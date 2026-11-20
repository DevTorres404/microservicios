package com.example.microservicios.inventario.reservation;

import com.example.microservicios.inventario.domain.Money;
import com.example.microservicios.inventario.domain.Reservation;
import com.example.microservicios.inventario.domain.ReservationLine;
import com.example.microservicios.inventario.domain.ReservationStatus;
import com.example.microservicios.inventario.domain.StockItem;
import com.example.microservicios.inventario.exception.InsufficientStockException;
import com.example.microservicios.inventario.exception.ReservationConflictException;
import com.example.microservicios.inventario.repository.ReservationLineRepository;
import com.example.microservicios.inventario.repository.ReservationRepository;
import com.example.microservicios.inventario.repository.StockRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies, replays and releases order reservations.
 *
 * <h2>Atomicity strategy</h2>
 *
 * Every line is decremented with a single conditional statement,
 * {@code UPDATE stock SET available = available - :quantity WHERE sku = :sku AND available >= :quantity},
 * inside one transaction. PostgreSQL locks the row for the duration of the transaction, and a
 * concurrent writer re-evaluates the predicate against the committed value, so the loser gets
 * {@code 0} updated rows instead of overselling. There is no read-then-write window to lose.
 *
 * <p>All lines are attempted to report every missing SKU, and any missing SKU throws
 * {@link InsufficientStockException}. Because it is a runtime exception, the transaction rolls back
 * and the decrements of the other lines disappear with it: the reservation is all-or-nothing.
 *
 * <h2>Idempotency strategy</h2>
 *
 * The order identifier is the idempotency key enforced by {@code uq_reservations_order}. Creation
 * uses {@code INSERT ... ON CONFLICT (order_id) DO NOTHING}; a losing insert returns {@code 0} and is
 * treated as a retry instead of aborting the transaction. A retry with the same quantities returns
 * the stored lines and price snapshot with {@code idempotent = true}; different quantities, or an
 * already released reservation, raise {@link ReservationConflictException}.
 *
 * <h2>Release strategy</h2>
 *
 * Release loads the reservation with {@code SELECT ... FOR UPDATE} and toggles the status, so two
 * concurrent releases restore the stock only once. Unknown orders and repeated releases return
 * {@code false} without failing.
 */
@Service
public class ReservationService {

    private static final Comparator<ReservationLineRequest> BY_SKU =
            Comparator.comparing(ReservationLineRequest::sku);

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationLineRepository reservationLineRepository;

    public ReservationService(StockRepository stockRepository,
                              ReservationRepository reservationRepository,
                              ReservationLineRepository reservationLineRepository) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
        this.reservationLineRepository = reservationLineRepository;
    }

    /**
     * Reserves every requested line for an order, or nothing at all.
     *
     * @throws InsufficientStockException when at least one line cannot be satisfied
     * @throws ReservationConflictException when the order already holds a different reservation
     */
    @Transactional
    public ReservationResult reserve(ReservationRequest request) {
        Optional<Reservation> existing = reservationRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), request);
        }

        int inserted = reservationRepository.insertIfAbsent(request.orderId());
        if (inserted == 0) {
            // Another transaction created the reservation between our read and our insert.
            Reservation concurrent = reservationRepository.findByOrderId(request.orderId())
                    .orElseThrow(() -> new ReservationConflictException(
                            "Reservation for order " + request.orderId() + " could not be loaded after insert conflict"));
            return replayOrConflict(concurrent, request);
        }

        List<ReservationLineRequest> ordered = request.lines().stream().sorted(BY_SKU).toList();
        List<String> missingSkus = new ArrayList<>();
        List<Money> unitPrices = new ArrayList<>(ordered.size());
        for (ReservationLineRequest line : ordered) {
            int updated = stockRepository.decrementIfAvailable(line.sku(), line.quantity());
            if (updated == 0) {
                missingSkus.add(line.sku());
                unitPrices.add(null);
                continue;
            }
            unitPrices.add(stockRepository.findById(line.sku())
                    .map(StockItem::getUnitPrice)
                    .map(Money::of)
                    .orElseThrow(() -> new IllegalStateException(
                            "Stock row vanished after a successful decrement for SKU " + line.sku())));
        }
        if (!missingSkus.isEmpty()) {
            throw new InsufficientStockException(missingSkus);
        }

        Reservation reservation = reservationRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Inserted reservation for order " + request.orderId() + " is not readable"));

        List<ReservationLine> persisted = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ReservationLineRequest line = ordered.get(index);
            persisted.add(new ReservationLine(reservation, request.orderId(), line.sku(), line.quantity(),
                    unitPrices.get(index)));
        }
        reservationLineRepository.saveAll(persisted);

        return resultFrom(request.orderId(), persisted, false);
    }

    /**
     * Restores the stock of an applied reservation.
     *
     * @return {@code true} when this call released it, {@code false} when the order has no reservation
     *         or it was already released
     */
    @Transactional
    public boolean release(long orderId) {
        Optional<Reservation> found = reservationRepository.findByOrderIdForUpdate(orderId);
        if (found.isEmpty()) {
            return false;
        }
        Reservation reservation = found.get();
        if (reservation.getStatus() == ReservationStatus.LIBERADA) {
            return false;
        }

        for (ReservationLine line : reservationLineRepository.findByOrderIdOrderBySkuAsc(orderId)) {
            stockRepository.restore(line.getSku(), line.getQuantity());
        }
        reservation.release(OffsetDateTime.now(ZoneOffset.UTC));
        return true;
    }

    private ReservationResult replayOrConflict(Reservation reservation, ReservationRequest request) {
        if (reservation.getStatus() == ReservationStatus.LIBERADA) {
            throw new ReservationConflictException(
                    "Reservation for order " + request.orderId() + " was already released");
        }

        List<ReservationLine> stored = reservationLineRepository
                .findByOrderIdOrderBySkuAsc(request.orderId());
        List<ReservationLineRequest> requested = request.lines().stream().sorted(BY_SKU).toList();

        boolean equivalent = stored.size() == requested.size();
        for (int index = 0; equivalent && index < requested.size(); index++) {
            ReservationLineRequest line = requested.get(index);
            ReservationLine saved = stored.get(index);
            equivalent = saved.getSku().equals(line.sku()) && saved.getQuantity() == line.quantity();
        }
        if (!equivalent) {
            throw new ReservationConflictException("Order " + request.orderId()
                    + " already has a reservation with different lines");
        }

        return resultFrom(request.orderId(), stored, true);
    }

    private ReservationResult resultFrom(long orderId, List<ReservationLine> lines, boolean idempotent) {
        List<ReservedLine> reserved = lines.stream()
                .map(line -> new ReservedLine(line.getSku(), line.getQuantity(), line.getUnitPrice()))
                .toList();
        Money total = reserved.stream()
                .map(line -> line.unitPrice().times(line.quantity()))
                .reduce(Money.zero(), Money::plus);
        return new ReservationResult(orderId, reserved, total, idempotent);
    }
}
