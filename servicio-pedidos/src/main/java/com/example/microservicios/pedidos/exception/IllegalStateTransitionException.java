package com.example.microservicios.pedidos.exception;

import com.example.microservicios.pedidos.domain.OrderStatus;

public class IllegalStateTransitionException extends DomainException {

    public static final String CODE = "ILLEGAL_STATE_TRANSITION";

    private final OrderStatus from;
    private final OrderStatus to;

    public IllegalStateTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }

    @Override
    public String code() {
        return CODE;
    }
}
