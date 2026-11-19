package com.example.microservicios.pedidos.domain;

import com.example.microservicios.pedidos.exception.IllegalStateTransitionException;
import com.example.microservicios.pedidos.exception.InvalidOrderException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Order aggregate root.
 *
 * <p>Creation accepts only the customer and the line drafts, so a client-supplied price or total has
 * no place to enter. The total starts at zero and is computed by
 * {@link #applyInventoryPrices(Map)} from the prices the inventory service reports.
 *
 * <p>State transitions ({@code confirmar}, {@code rechazar}, {@code cancelar}) are implemented in a
 * later work unit; this slice only guarantees the initial {@link OrderStatus#PENDIENTE} state.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "customer", nullable = false, length = 120)
    private String customer;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    /** Required by JPA. */
    protected Order() {
    }

    /**
     * Creates a pending order. The total is zero until inventory prices are applied.
     *
     * @param customer non-blank customer name
     * @param drafts at least one line, positive quantities and no repeated SKU
     * @throws InvalidOrderException when the input does not satisfy those rules
     */
    public static Order create(String customer, List<OrderLineDraft> drafts) {
        if (customer == null || customer.isBlank()) {
            throw new InvalidOrderException("Customer must not be blank");
        }
        if (drafts == null || drafts.isEmpty()) {
            throw new InvalidOrderException("An order requires at least one line");
        }

        Order order = new Order();
        order.customer = customer.trim();
        order.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        order.status = OrderStatus.PENDIENTE;
        order.total = Money.zero().amount();

        Set<String> seen = new HashSet<>();
        for (OrderLineDraft draft : drafts) {
            Objects.requireNonNull(draft, "draft");
            if (draft.sku() == null || draft.sku().isBlank()) {
                throw new InvalidOrderException("Order line SKU must not be blank");
            }
            if (draft.quantity() <= 0) {
                throw new InvalidOrderException(
                        "Order line quantity must be positive for SKU " + draft.sku());
            }
            if (!seen.add(draft.sku())) {
                throw new InvalidOrderException("Duplicated SKU in order: " + draft.sku());
            }
            order.lines.add(new OrderLine(order, draft.sku(), draft.quantity()));
        }
        return order;
    }

    /**
     * Merges the current inventory prices into the lines and recomputes the total.
     *
     * @param prices unit price per SKU reported by the inventory service
     * @throws InvalidOrderException when a line has no reported price
     */
    public void applyInventoryPrices(Map<String, Money> prices) {
        Objects.requireNonNull(prices, "prices");
        Money computed = Money.zero();
        for (OrderLine line : lines) {
            Money unitPrice = prices.get(line.getSku());
            if (unitPrice == null) {
                throw new InvalidOrderException(
                        "Inventory did not report a price for SKU " + line.getSku());
            }
            line.applyPrice(unitPrice);
            computed = computed.plus(unitPrice.times(line.getQuantity()));
        }
        this.total = computed.amount();
    }

    public void confirm() {
        if (this.status != OrderStatus.PENDIENTE) {
            throw new IllegalStateTransitionException(this.status, OrderStatus.CONFIRMADO);
        }
        this.status = OrderStatus.CONFIRMADO;
    }

    public void reject() {
        if (this.status != OrderStatus.PENDIENTE) {
            throw new IllegalStateTransitionException(this.status, OrderStatus.RECHAZADO);
        }
        this.status = OrderStatus.RECHAZADO;
    }

    public void cancel() {
        if (this.status != OrderStatus.PENDIENTE && this.status != OrderStatus.CONFIRMADO) {
            throw new IllegalStateTransitionException(this.status, OrderStatus.CANCELADO);
        }
        this.status = OrderStatus.CANCELADO;
    }

    public Long getId() {
        return id;
    }

    public String getCustomer() {
        return customer;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Money getTotal() {
        return Money.of(total);
    }

    public List<OrderLine> getLines() {
        return List.copyOf(lines);
    }
}
