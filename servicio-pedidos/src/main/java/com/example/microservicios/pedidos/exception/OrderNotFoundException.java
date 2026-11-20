package com.example.microservicios.pedidos.exception;

public class OrderNotFoundException extends DomainException {

    public static final String CODE = "ORDER_NOT_FOUND";

    private final Long orderId;

    public OrderNotFoundException(Long orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }

    public Long orderId() {
        return orderId;
    }

    @Override
    public String code() {
        return CODE;
    }
}
