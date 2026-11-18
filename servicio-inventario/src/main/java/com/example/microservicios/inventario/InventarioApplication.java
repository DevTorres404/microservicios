package com.example.microservicios.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Inventory service. Owns {@code inventario_db}: stock per SKU and reservations per order.
 * It does not know that {@code servicio-pedidos} exists.
 */
@SpringBootApplication
public class InventarioApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventarioApplication.class, args);
    }
}
