package com.example.microservicios.inventario.web;

import com.example.microservicios.inventario.exception.DomainException;
import com.example.microservicios.inventario.exception.InsufficientStockException;
import com.example.microservicios.inventario.exception.InvalidReservationException;
import com.example.microservicios.inventario.exception.ReservationConflictException;
import com.example.microservicios.inventario.exception.SkuNotFoundException;
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
public class InventarioExceptionHandler {

    @ExceptionHandler(SkuNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleSkuNotFound(SkuNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.NOT_FOUND, "SKU Not Found", ex.getMessage(),
                ex.code(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.CONFLICT, "Insufficient Stock", ex.getMessage(),
                "INSUFFICIENT_STOCK", request);
        pd.setProperty("missingSkus", ex.missingSkus());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<ProblemDetail> handleReservationConflict(ReservationConflictException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.CONFLICT, "Reservation Conflict", ex.getMessage(),
                "RESERVATION_CONFLICT", request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(InvalidReservationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidReservation(InvalidReservationException ex, HttpServletRequest request) {
        ProblemDetail pd = createProblemDetail(HttpStatus.BAD_REQUEST, "Invalid Reservation", ex.getMessage(),
                "INVALID_RESERVATION", request);
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
