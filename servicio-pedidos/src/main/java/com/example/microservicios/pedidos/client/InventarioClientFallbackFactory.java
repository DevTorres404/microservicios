package com.example.microservicios.pedidos.client;

import com.example.microservicios.pedidos.exception.InsufficientStockException;
import com.example.microservicios.pedidos.exception.InventarioUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class InventarioClientFallbackFactory implements FallbackFactory<InventarioClient> {

    private static final Logger log = LoggerFactory.getLogger(InventarioClientFallbackFactory.class);

    @Override
    public InventarioClient create(Throwable cause) {
        if (cause instanceof InsufficientStockException ise) {
            throw ise;
        }

        return new InventarioClient() {
            @Override
            public ReservationResponseDto reserve(ReservationRequestDto request) {
                log.warn("Fallback triggered for reserve (orderId: {}): {}",
                        request.orderId(), cause.getMessage());
                throw new InventarioUnavailableException(
                        "Inventory service is unavailable: " + cause.getMessage(), cause);
            }

            @Override
            public void release(long pedidoId) {
                log.warn("Fallback triggered for release (orderId: {}): {}",
                        pedidoId, cause.getMessage());
                throw new InventarioUnavailableException(
                        "Inventory service is unavailable: " + cause.getMessage(), cause);
            }
        };
    }
}
