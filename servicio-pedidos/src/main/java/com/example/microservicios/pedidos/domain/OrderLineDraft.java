package com.example.microservicios.pedidos.domain;

/**
 * A line as supplied by the client: only what the client knows.
 *
 * <p>It deliberately contains no price and no total. Prices come from the inventory service, so there
 * is no field through which a caller could inject the amount used for the order total.
 *
 * @param sku catalogue identifier of the product
 * @param quantity units requested; must be strictly positive
 */
public record OrderLineDraft(String sku, int quantity) {
}
