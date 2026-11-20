package com.example.microservicios.pedidos.exception;

public class InventarioUnavailableException extends DomainException {

    public static final String CODE = "INVENTARIO_NO_DISPONIBLE";

    public InventarioUnavailableException(String message) {
        super(message);
    }

    public InventarioUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return CODE;
    }
}
