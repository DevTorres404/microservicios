package com.example.microservicios.inventario.exception;

/**
 * Raised when a reservation request is structurally invalid and must not reach the database.
 */
public class InvalidReservationException extends DomainException {

    public static final String CODE = "RESERVA_INVALIDA";

    public InvalidReservationException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return CODE;
    }
}
