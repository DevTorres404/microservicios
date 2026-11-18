package com.example.microservicios.pedidos.exception;

/**
 * Base type of every expected domain failure.
 *
 * <p>It carries a stable contract code and never an HTTP status: mapping codes to transport is the
 * responsibility of the API layer, which does not exist in this slice.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    /** Stable, language-neutral identifier of the failure kind. */
    public abstract String code();
}
