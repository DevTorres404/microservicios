package com.example.microservicios.pedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Order service. Owns {@code pedidos_db}: orders, their lines and their lifecycle.
 * Stock data is only ever obtained from {@code servicio-inventario} over HTTP.
 */
@SpringBootApplication
@EnableFeignClients
public class PedidosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PedidosApplication.class, args);
    }
}
