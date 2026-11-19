package com.example.microservicios.pedidos.client;

import java.math.BigDecimal;
import java.util.List;

public record ReservationResponseDto(
        long orderId,
        List<ReservedLineDto> lines,
        BigDecimal total,
        boolean idempotent
) {}
