package com.example.microservicios.inventario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Stock of one SKU: the available quantity and the current unit price.
 *
 * <p>This is the only place where availability lives. Writes always happen on the primary node and
 * the {@code available >= 0} invariant is enforced twice: here and by a database check constraint.
 */
@Entity
@Table(name = "stock")
public class StockItem {

    @Id
    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "available", nullable = false)
    private int available;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Required by JPA. */
    protected StockItem() {
    }

    public StockItem(String sku, int available, BigDecimal unitPrice) {
        this.sku = Objects.requireNonNull(sku, "sku");
        if (available < 0) {
            throw new IllegalArgumentException("Available stock cannot be negative: " + available);
        }
        this.available = available;
        this.unitPrice = Money.of(unitPrice).amount();
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
