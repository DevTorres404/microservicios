package com.example.microservicios.pedidos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Monetary amount normalised to scale 2 with {@link RoundingMode#HALF_UP} at every construction.
 *
 * <p>{@code double} and {@code float} are not accepted on purpose: binary floating point cannot
 * represent decimal money exactly, and this project forbids it for amounts.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Monetary amounts cannot be negative: " + amount);
        }
        return new Money(amount);
    }

    /** Convenience factory for tests and configuration values expressed as decimal text. */
    public static Money of(String amount) {
        return of(new BigDecimal(amount));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(amount.add(other.amount));
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative: " + quantity);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public BigDecimal amount() {
        return amount;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
