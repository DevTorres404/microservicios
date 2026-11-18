package com.example.microservicios.pedidos.repository;

import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access to {@code orders} and {@code order_lines}.
 *
 * <p>Every query names only order-service tables. Inventory data never appears here: stock and prices
 * arrive over the HTTP contract of the inventory service.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Critical read: one order with its lines, from the primary node (wiring belongs to W6). */
    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<Order> findById(Long id);

    /** Serves {@code GET /api/pedidos?estado=...} with the lines already fetched, newest first. */
    @Query("""
            select distinct o from Order o
            left join fetch o.lines
            where o.status = :status
            order by o.createdAt desc, o.id desc
            """)
    List<Order> findByStatusWithLines(@Param("status") OrderStatus status);

    /** Serves the unfiltered listing with the lines already fetched, newest first. */
    @Query("""
            select distinct o from Order o
            left join fetch o.lines
            order by o.createdAt desc, o.id desc
            """)
    List<Order> findAllWithLines();
}
