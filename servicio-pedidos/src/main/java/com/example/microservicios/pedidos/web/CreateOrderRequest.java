package com.example.microservicios.pedidos.web;

import com.example.microservicios.pedidos.domain.OrderLineDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "Customer name must not be blank")
        String customer,

        @NotEmpty(message = "Order must have at least one line")
        @Valid
        List<CreateOrderLineRequest> lines
) {
    public List<OrderLineDraft> toDrafts() {
        return lines.stream()
                .map(line -> new OrderLineDraft(line.sku(), line.quantity()))
                .toList();
    }

    public record CreateOrderLineRequest(
            @NotBlank(message = "SKU must not be blank")
            String sku,

            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            Integer quantity
    ) {}
}
