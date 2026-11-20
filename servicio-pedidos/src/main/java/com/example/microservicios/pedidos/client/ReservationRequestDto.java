package com.example.microservicios.pedidos.client;

import java.util.List;

public record ReservationRequestDto(
        long orderId,
        List<ReservationLineDto> lines
) {}
