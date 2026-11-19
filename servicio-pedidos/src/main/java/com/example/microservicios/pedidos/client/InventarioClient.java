package com.example.microservicios.pedidos.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "servicio-inventario", path = "/api/inventario", fallbackFactory = InventarioClientFallbackFactory.class, configuration = FeignClientConfiguration.class)
public interface InventarioClient {

    @PostMapping("/reservas")
    ReservationResponseDto reserve(@RequestBody ReservationRequestDto request);

    @PostMapping("/reservas/{pedidoId}/liberacion")
    void release(@PathVariable("pedidoId") long pedidoId);
}
