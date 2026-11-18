package com.example.microservicios.inventario.repository;

import com.example.microservicios.inventario.domain.Reservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access to {@code reservations}.
 *
 * <p>Reservation creation is deliberately not a JPA {@code persist}: a unique violation would abort
 * the transaction. The native {@code ON CONFLICT DO NOTHING} turns the duplicate into an observable
 * row count instead, so a retry can be replayed without poisoning the surrounding transaction.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByOrderId(Long orderId);

    /**
     * Loads a reservation taking a pessimistic row lock, used by the release path so two concurrent
     * releases cannot restore the same units twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.orderId = :orderId")
    Optional<Reservation> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    /**
     * Inserts the reservation for an order only when none exists.
     *
     * @return {@code 1} when this caller created the reservation, {@code 0} when it already existed
     */
    @Modifying
    @Query(value = """
            insert into reservations (order_id, status)
            values (:orderId, 'APLICADA')
            on conflict (order_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("orderId") Long orderId);
}
