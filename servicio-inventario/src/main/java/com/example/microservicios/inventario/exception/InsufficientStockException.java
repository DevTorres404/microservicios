package com.example.microservicios.inventario.exception;

import java.util.Collection;
import java.util.List;

/**
 * Raised when at least one line of a reservation cannot be satisfied.
 *
 * <p>The whole reservation is rolled back, so the reported SKUs describe what was missing without any
 * partial effect on stock.
 */
public class InsufficientStockException extends DomainException {

    public static final String CODE = "STOCK_INSUFICIENTE";

    private final List<String> missingSkus;

    public InsufficientStockException(Collection<String> missingSkus) {
        super("Insufficient stock for SKUs: " + String.join(", ", missingSkus));
        this.missingSkus = List.copyOf(missingSkus);
        if (this.missingSkus.isEmpty()) {
            throw new IllegalArgumentException("At least one missing SKU is required");
        }
    }

    public List<String> missingSkus() {
        return missingSkus;
    }

    @Override
    public String code() {
        return CODE;
    }
}
