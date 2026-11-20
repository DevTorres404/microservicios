package com.example.microservicios.inventario.exception;

/**
 * Raised when an order already has a reservation that cannot be replayed: different quantities, or a
 * reservation that was already released.
 */
public class ReservationConflictException extends DomainException {

    public static final String CODE = "RESERVA_CONFLICTO";

    public ReservationConflictException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return CODE;
    }
}
