package com.example.microservicios.inventario.web;

import com.example.microservicios.inventario.domain.StockItem;
import java.math.BigDecimal;

public record StockResponse(String sku, int available, BigDecimal unitPrice) {

    public static StockResponse from(StockItem item) {
        return new StockResponse(item.getSku(), item.getAvailable(), item.getUnitPrice());
    }
}
