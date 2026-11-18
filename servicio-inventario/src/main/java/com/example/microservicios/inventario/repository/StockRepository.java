package com.example.microservicios.inventario.repository;

import com.example.microservicios.inventario.domain.StockItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access to {@code stock}.
 *
 * <p>Availability is mutated only through the conditional decrement below. Letting PostgreSQL decide
 * with {@code available >= :quantity} in a single statement is what makes concurrent reservations
 * safe: the row lock serialises the two writers and the re-evaluated predicate rejects the loser.
 */
public interface StockRepository extends JpaRepository<StockItem, String> {

    List<StockItem> findAllByOrderBySkuAsc();

    /**
     * Decrements the available quantity only when the stock is sufficient.
     *
     * @return {@code 1} when the row was updated, {@code 0} when the SKU does not exist or lacks stock
     */
    @Modifying
    @Query(value = """
            update stock
               set available = available - :quantity,
                   updated_at = now()
             where sku = :sku
               and available >= :quantity
            """, nativeQuery = true)
    int decrementIfAvailable(@Param("sku") String sku, @Param("quantity") int quantity);

    /** Restores previously reserved units. Used only when releasing an applied reservation. */
    @Modifying
    @Query(value = """
            update stock
               set available = available + :quantity,
                   updated_at = now()
             where sku = :sku
            """, nativeQuery = true)
    int restore(@Param("sku") String sku, @Param("quantity") int quantity);
}
