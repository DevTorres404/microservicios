package com.example.microservicios.pedidos.web;

import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLine;
import com.example.microservicios.pedidos.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String customer,
        OffsetDateTime createdAt,
        OrderStatus status,
        BigDecimal total,
        List<OrderLineResponse> lines
) {
    public static OrderResponse from(Order order) {
        List<OrderLineResponse> lineResponses = order.getLines().stream()
                .map(OrderLineResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomer(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getTotal().amount(),
                lineResponses
        );
    }

    public record OrderLineResponse(
            Long id,
            String sku,
            int quantity,
            BigDecimal unitPrice
    ) {
        public static OrderLineResponse from(OrderLine line) {
            BigDecimal price = line.getUnitPrice() != null ? line.getUnitPrice().amount() : null;
            return new OrderLineResponse(line.getId(), line.getSku(), line.getQuantity(), price);
        }
    }
}
