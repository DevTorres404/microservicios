package com.example.microservicios.inventario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * One reservation per order.
 *
 * <p>The row is created with a native {@code INSERT ... ON CONFLICT (order_id) DO NOTHING} so that a
 * duplicate request never scales the stock twice and never leaves the transaction in an aborted
 * state, which is what a plain unique-violation would do.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    /** Required by JPA. */
    protected Reservation() {
    }

    public Reservation(Long orderId) {
        this(orderId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public Reservation(Long orderId, OffsetDateTime createdAt) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.status = ReservationStatus.APLICADA;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Marks this reservation as released. Callers must hold a transaction and a pessimistic lock. */
    public void release(OffsetDateTime releasedAt) {
        if (status == ReservationStatus.LIBERADA) {
            throw new IllegalStateException("Reservation for order " + orderId + " is already released");
        }
        this.status = ReservationStatus.LIBERADA;
        this.releasedAt = Objects.requireNonNull(releasedAt, "releasedAt");
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }
}
