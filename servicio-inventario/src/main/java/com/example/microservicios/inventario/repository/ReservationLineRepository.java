package com.example.microservicios.inventario.repository;

import com.example.microservicios.inventario.domain.ReservationLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@code reservation_lines}, always ordered by SKU for deterministic output. */
public interface ReservationLineRepository extends JpaRepository<ReservationLine, Long> {

    List<ReservationLine> findByOrderIdOrderBySkuAsc(Long orderId);
}
