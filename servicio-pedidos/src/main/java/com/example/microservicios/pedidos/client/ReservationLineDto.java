package com.example.microservicios.pedidos.client;

public record ReservationLineDto(
        String sku,
        int quantity
) {}
