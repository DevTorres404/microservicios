package com.example.microservicios.pedidos.web;

import com.example.microservicios.pedidos.exception.DomainException;
import com.example.microservicios.pedidos.exception.IllegalStateTransitionException;
import com.example.microservicios.pedidos.exception.InsufficientStockException;
import com.example.microservicios.pedidos.exception.InventarioUnavailableException;
import com.example.microservicios.pedidos.exception.InvalidOrderException;
import com.example.microservicios.pedidos.exception.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PedidosExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.NOT_FOUND, "Order Not Found", ex.getMessage(),
                ex.code(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleIllegalStateTransition(IllegalStateTransitionException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.CONFLICT, "Illegal State Transition", ex.getMessage(),
                ex.code(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.CONFLICT, "Insufficient Stock", ex.getMessage(),
                "INSUFFICIENT_STOCK", request);
        pd.setProperty("missingSkus", ex.missingSkus());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(InventarioUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleInventarioUnavailable(InventarioUnavailableException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, "Inventory Unavailable", ex.getMessage(),
                ex.code(), request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
    }

    @ExceptionHandler(com.example.microservicios.persistence.routing.DbNodeUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleDbNodeUnavailable(com.example.microservicios.persistence.routing.DbNodeUnavailableException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, "Database Node Unavailable", ex.getMessage(),
                ex.code(), request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrder(InvalidOrderException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.BAD_REQUEST, "Invalid Order", ex.getMessage(),
                "VALIDATION_ERROR", request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(),
                "BAD_REQUEST", request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.BAD_REQUEST, "Validation Error",
                "Request validation failed", "VALIDATION_ERROR", request);
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        pd.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    private ProblemDetail createProblemDetail(HttpStatus status, String title, String detail,
                                              String code, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("urn:problem-type:" + code.toLowerCase().replace('_', '-')));
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());

        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            Object attr = request.getAttribute("correlationId");
            correlationId = (attr != null) ? attr.toString() : UUID.randomUUID().toString();
        }
        pd.setProperty("correlationId", correlationId);
        return pd;
    }
}
