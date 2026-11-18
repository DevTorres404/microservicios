package com.example.microservicios.pedidos.exception;

/**
 * Raised when an order cannot be built from the supplied data.
 */
public class InvalidOrderException extends DomainException {

    public static final String CODE = "PEDIDO_INVALIDO";

    public InvalidOrderException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return CODE;
    }
}
