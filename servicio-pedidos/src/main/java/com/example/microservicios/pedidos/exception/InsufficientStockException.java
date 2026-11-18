package com.example.microservicios.pedidos.exception;

import java.util.Collection;
import java.util.List;

/**
 * Raised when inventory reports that at least one line cannot be reserved.
 *
 * <p>It lives in the order service because the API layer will translate it into "order rejected"
 * without leaking any HTTP concern into the domain.
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
