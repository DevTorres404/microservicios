package com.example.microservicios.persistence.routing;

/**
 * Exception thrown when a database node pool fails to loan a physical connection
 * or is unreachable. Mapped by REST controllers to HTTP 503 Service Unavailable (RFC 9457)
 * with stable error code {@code DB_NODE_UNAVAILABLE}.
 */
public class DbNodeUnavailableException extends RuntimeException {

    public static final String ERROR_CODE = "DB_NODE_UNAVAILABLE";

    private final String code;

    public DbNodeUnavailableException(String message) {
        super(message);
        this.code = ERROR_CODE;
    }

    public DbNodeUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.code = ERROR_CODE;
    }

    public String code() {
        return code;
    }
}
