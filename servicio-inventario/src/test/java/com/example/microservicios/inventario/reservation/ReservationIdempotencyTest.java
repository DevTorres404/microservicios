package com.example.microservicios.inventario.reservation;

import com.example.microservicios.inventario.domain.Money;
import com.example.microservicios.inventario.domain.Reservation;
import com.example.microservicios.inventario.domain.ReservationLine;
import com.example.microservicios.inventario.domain.ReservationStatus;
import com.example.microservicios.inventario.domain.StockItem;
import com.example.microservicios.inventario.exception.ReservationConflictException;
import com.example.microservicios.inventario.repository.ReservationLineRepository;
import com.example.microservicios.inventario.repository.ReservationRepository;
import com.example.microservicios.inventario.repository.StockRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationIdempotencyTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationLineRepository reservationLineRepository;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(stockRepository, reservationRepository, reservationLineRepository);
    }

    @Test
    @DisplayName("Initial reservation succeeds with idempotent = false and decrements stock")
    void shouldReserveOnFirstAttempt() {
        ReservationRequest request = new ReservationRequest(100L, List.of(new ReservationLineRequest("SKU-1", 2)));

        given(reservationRepository.findByOrderId(100L)).willReturn(Optional.empty());
        given(reservationRepository.insertIfAbsent(100L)).willReturn(1);
        given(stockRepository.decrementIfAvailable("SKU-1", 2)).willReturn(1);
        given(stockRepository.findById("SKU-1")).willReturn(Optional.of(new StockItem("SKU-1", 8, new BigDecimal("15.00"))));

        Reservation storedReservation = new Reservation(100L);
        given(reservationRepository.findByOrderId(100L)).willReturn(Optional.empty(), Optional.of(storedReservation));

        ReservationResult result = reservationService.reserve(request);

        assertThat(result.orderId()).isEqualTo(100L);
        assertThat(result.idempotent()).isFalse();
        assertThat(result.total().amount()).isEqualByComparingTo("30.00");
        verify(stockRepository, times(1)).decrementIfAvailable("SKU-1", 2);
    }

    @Test
    @DisplayName("Replaying reservation with same quantities returns equivalent result without decrementing stock again")
    void shouldReplayReservationIdempotently() {
        ReservationRequest request = new ReservationRequest(100L, List.of(new ReservationLineRequest("SKU-1", 2)));

        Reservation existingReservation = new Reservation(100L);
        given(reservationRepository.findByOrderId(100L)).willReturn(Optional.of(existingReservation));

        ReservationLine storedLine = new ReservationLine(existingReservation, 100L, "SKU-1", 2, Money.of("15.00"));
        given(reservationLineRepository.findByOrderIdOrderBySkuAsc(100L)).willReturn(List.of(storedLine));

        ReservationResult replayResult = reservationService.reserve(request);

        assertThat(replayResult.orderId()).isEqualTo(100L);
        assertThat(replayResult.idempotent()).isTrue();
        assertThat(replayResult.total().amount()).isEqualByComparingTo("30.00");

        // Verify stock decrement was NEVER called on replay
        verify(stockRepository, never()).decrementIfAvailable(anyString(), anyInt());
    }

    @Test
    @DisplayName("Replaying reservation with different quantities throws ReservationConflictException")
    void shouldRejectReplayWithDifferentQuantities() {
        ReservationRequest requestWithDifferentQty = new ReservationRequest(100L, List.of(new ReservationLineRequest("SKU-1", 5)));

        Reservation existingReservation = new Reservation(100L);
        given(reservationRepository.findByOrderId(100L)).willReturn(Optional.of(existingReservation));

        ReservationLine storedLine = new ReservationLine(existingReservation, 100L, "SKU-1", 2, Money.of("15.00"));
        given(reservationLineRepository.findByOrderIdOrderBySkuAsc(100L)).willReturn(List.of(storedLine));

        assertThatThrownBy(() -> reservationService.reserve(requestWithDifferentQty))
                .isInstanceOf(ReservationConflictException.class)
                .hasMessageContaining("different lines");

        verify(stockRepository, never()).decrementIfAvailable(anyString(), anyInt());
    }

    @Test
    @DisplayName("Releasing already released reservation is idempotent and does not restore stock twice")
    void shouldReleaseIdempotentlyWithoutDoubleRestore() {
        Reservation releasedReservation = new Reservation(100L);
        releasedReservation.release(java.time.OffsetDateTime.now());

        given(reservationRepository.findByOrderIdForUpdate(100L)).willReturn(Optional.of(releasedReservation));

        boolean releasedAgain = reservationService.release(100L);

        assertThat(releasedAgain).isFalse();
        verify(stockRepository, never()).restore(anyString(), anyInt());
    }
}
