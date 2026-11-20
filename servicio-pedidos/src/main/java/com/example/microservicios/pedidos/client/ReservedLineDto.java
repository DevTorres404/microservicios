package com.example.microservicios.pedidos.client;

import java.math.BigDecimal;

public record ReservedLineDto(
        String sku,
        int quantity,
        BigDecimal unitPrice
) {}
