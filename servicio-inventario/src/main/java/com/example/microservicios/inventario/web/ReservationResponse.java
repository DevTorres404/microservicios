package com.example.microservicios.inventario.web;

import com.example.microservicios.inventario.reservation.ReservationResult;
import java.math.BigDecimal;
import java.util.List;

public record ReservationResponse(
        long orderId,
        List<ReservedLineDto> lines,
        BigDecimal total,
        boolean idempotent
) {
    public static ReservationResponse from(ReservationResult result) {
        List<ReservedLineDto> lines = result.lines().stream()
                .map(line -> new ReservedLineDto(line.sku(), line.quantity(), line.unitPrice().amount()))
                .toList();
        return new ReservationResponse(result.orderId(), lines, result.total().amount(), result.idempotent());
    }

    public record ReservedLineDto(String sku, int quantity, BigDecimal unitPrice) {}
}
