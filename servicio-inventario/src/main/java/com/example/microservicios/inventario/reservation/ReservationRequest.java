package com.example.microservicios.inventario.reservation;

import com.example.microservicios.inventario.exception.InvalidReservationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A request to reserve stock for one order.
 *
 * <p>Validation happens at construction time so an invalid request can never reach a transaction.
 *
 * @param orderId identifier of the order that owns the reservation; it is the idempotency key
 * @param lines requested lines; at least one, no duplicate SKU, strictly positive quantities
 */
public record ReservationRequest(long orderId, List<ReservationLineRequest> lines) {

    public ReservationRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
        requireValid(orderId, lines);
    }

    private static void requireValid(long orderId, List<ReservationLineRequest> lines) {
        if (orderId <= 0) {
            throw new InvalidReservationException("Order identifier must be positive: " + orderId);
        }
        if (lines.isEmpty()) {
            throw new InvalidReservationException("A reservation requires at least one line");
        }
        Set<String> seen = new HashSet<>();
        for (ReservationLineRequest line : lines) {
            Objects.requireNonNull(line, "line");
            if (line.sku() == null || line.sku().isBlank()) {
                throw new InvalidReservationException("Reservation line SKU must not be blank");
            }
            if (line.quantity() <= 0) {
                throw new InvalidReservationException(
                        "Reservation line quantity must be positive for SKU " + line.sku());
            }
            if (!seen.add(line.sku())) {
                throw new InvalidReservationException("Duplicated SKU in reservation: " + line.sku());
            }
        }
    }
}
