package com.example.microservicios.inventario.domain;

/**
 * Lifecycle of a reservation.
 *
 * <p>The persisted values are part of the SQL contract ({@code ck_reservations_status}), so they keep
 * the Spanish literals declared by the specification.
 */
public enum ReservationStatus {

    /** Stock was decremented for every line of the order. */
    APLICADA,

    /** Stock was restored and the order can no longer reuse this reservation. */
    LIBERADA
}
