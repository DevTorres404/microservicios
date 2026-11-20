package com.example.microservicios.inventario.domain;

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
 * One reserved SKU of a reservation.
 *
 * <p>{@code unitPrice} is a snapshot taken when the reservation was applied. Keeping it here is what
 * makes an idempotent replay return a total equivalent to the first attempt even if the catalogue
 * price changed in between.
 */
@Entity
@Table(name = "reservation_lines")
public class ReservationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    /** Required by JPA. */
    protected ReservationLine() {
    }

    public ReservationLine(Reservation reservation, Long orderId, String sku, int quantity, Money unitPrice) {
        this.reservation = Objects.requireNonNull(reservation, "reservation");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.sku = Objects.requireNonNull(sku, "sku");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reserved quantity must be positive: " + quantity);
        }
        this.quantity = quantity;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice").amount();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return Money.of(unitPrice);
    }
}
