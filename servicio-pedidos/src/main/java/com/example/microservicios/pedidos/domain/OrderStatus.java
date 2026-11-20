package com.example.microservicios.pedidos.domain;

/**
 * Lifecycle of an order.
 *
 * <p>The persisted values are part of the SQL contract ({@code ck_orders_status}) and of the public
 * API, so they keep the Spanish literals declared by the specification. Transitions are implemented
 * in a later work unit; this slice only creates orders in {@link #PENDIENTE}.
 */
public enum OrderStatus {

    /** Created, waiting for the inventory reservation to decide the outcome. */
    PENDIENTE,

    /** Inventory reserved every line. */
    CONFIRMADO,

    /** Inventory rejected at least one line. */
    RECHAZADO,

    /** A confirmed order was cancelled and its reservation released. */
    CANCELADO
}
