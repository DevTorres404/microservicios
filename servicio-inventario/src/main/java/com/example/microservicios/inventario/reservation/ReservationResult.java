package com.example.microservicios.inventario.reservation;

import com.example.microservicios.inventario.domain.Money;
import java.util.List;

/**
 * Outcome of a reservation request.
 *
 * @param orderId order that owns the reservation
 * @param lines reserved lines with their price snapshot, ordered by SKU
 * @param total sum of {@code unitPrice * quantity}
 * @param idempotent {@code true} when this is a replay of an already applied reservation
 */
public record ReservationResult(long orderId, List<ReservedLine> lines, Money total, boolean idempotent) {

    public ReservationResult {
        lines = List.copyOf(lines);
    }
}
