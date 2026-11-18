package com.example.microservicios.pedidos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of an order.
 *
 * <p>{@code unitPrice} starts as {@code null}: the price is only known after the inventory service
 * answers. It is filled by {@link Order#applyInventoryPrices(java.util.Map)} and never by client
 * input.
 */
@Entity
@Table(name = "order_lines")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 19, scale = 2)
    private BigDecimal unitPrice;

    /** Required by JPA. */
    protected OrderLine() {
    }

    OrderLine(Order order, String sku, int quantity) {
        this.order = Objects.requireNonNull(order, "order");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.quantity = quantity;
    }

    void applyPrice(Money unitPrice) {
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice").amount();
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    /**
     * Unit price applied by the order when inventory prices were merged.
     *
     * @return the price, or {@code null} while the order has not received inventory prices yet
     */
    public Money getUnitPrice() {
        return unitPrice == null ? null : Money.of(unitPrice);
    }
}
