package com.example.microservicios.inventario.reservation;

/**
 * One requested line of a reservation: how many units of a SKU the order needs.
 *
 * @param sku catalogue identifier of the product
 * @param quantity units requested; must be strictly positive
 */
public record ReservationLineRequest(String sku, int quantity) {
}
