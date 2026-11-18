package com.example.microservicios.inventario.reservation;

import com.example.microservicios.inventario.domain.Money;

/**
 * A line that was actually reserved, including the unit price snapshot used for the total.
 */
public record ReservedLine(String sku, int quantity, Money unitPrice) {
}
