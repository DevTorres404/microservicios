package com.example.microservicios.inventario.web;

import com.example.microservicios.inventario.domain.Money;
import com.example.microservicios.inventario.domain.StockItem;
import com.example.microservicios.inventario.exception.InsufficientStockException;
import com.example.microservicios.inventario.exception.ReservationConflictException;
import com.example.microservicios.inventario.repository.StockRepository;
import com.example.microservicios.inventario.reservation.ReservationLineRequest;
import com.example.microservicios.inventario.reservation.ReservationRequest;
import com.example.microservicios.inventario.reservation.ReservationResult;
import com.example.microservicios.inventario.reservation.ReservationService;
import com.example.microservicios.inventario.reservation.ReservedLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventarioController.class)
class InventarioReservaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockRepository stockRepository;

    @MockBean
    private ReservationService reservationService;

    @Test
    @DisplayName("POST /api/inventario/reservas creates reservation successfully")
    void shouldCreateReservation() throws Exception {
        ReservationResult result = new ReservationResult(
                123L,
                List.of(new ReservedLine("SKU-1", 2, Money.of("10.00"))),
                Money.of("20.00"),
                false
        );
        given(reservationService.reserve(any(ReservationRequest.class))).willReturn(result);

        String payload = """
                {
                    "orderId": 123,
                    "lines": [
                        {"sku": "SKU-1", "quantity": 2}
                    ]
                }
                """;

        mockMvc.perform(post("/api/inventario/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(123))
                .andExpect(jsonPath("$.lines[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(20.00))
                .andExpect(jsonPath("$.idempotent").value(false));
    }

    @Test
    @DisplayName("POST /api/inventario/reservas returns 409 when insufficient stock")
    void shouldReturn409WhenInsufficientStock() throws Exception {
        given(reservationService.reserve(any(ReservationRequest.class)))
                .willThrow(new InsufficientStockException(List.of("SKU-1", "SKU-2")));

        String payload = """
                {
                    "orderId": 123,
                    "lines": [
                        {"sku": "SKU-1", "quantity": 100}
                    ]
                }
                """;

        mockMvc.perform(post("/api/inventario/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.missingSkus[0]").value("SKU-1"))
                .andExpect(jsonPath("$.missingSkus[1]").value("SKU-2"));
    }

    @Test
    @DisplayName("POST /api/inventario/reservas returns 409 on reservation conflict")
    void shouldReturn409OnReservationConflict() throws Exception {
        given(reservationService.reserve(any(ReservationRequest.class)))
                .willThrow(new ReservationConflictException("Different quantities for existing reservation"));

        String payload = """
                {
                    "orderId": 123,
                    "lines": [
                        {"sku": "SKU-1", "quantity": 5}
                    ]
                }
                """;

        mockMvc.perform(post("/api/inventario/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
    }

    @Test
    @DisplayName("POST /api/inventario/reservas/{pedidoId}/liberacion releases reservation idempotently")
    void shouldReleaseReservation() throws Exception {
        given(reservationService.release(123L)).willReturn(true);

        mockMvc.perform(post("/api/inventario/reservas/123/liberacion"))
                .andExpect(status().isOk());

        verify(reservationService).release(123L);
    }

    @Test
    @DisplayName("PUT /api/inventario/{sku} updates stock item")
    void shouldUpdateStockItem() throws Exception {
        StockItem item = new StockItem("SKU-1", 10, new BigDecimal("15.00"));
        given(stockRepository.findById("SKU-1")).willReturn(Optional.of(item));

        String payload = """
                {
                    "quantity": 50
                }
                """;

        mockMvc.perform(put("/api/inventario/SKU-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.available").value(50))
                .andExpect(jsonPath("$.unitPrice").value(15.00));
    }

    @Test
    @DisplayName("PUT /api/inventario/{sku} rejects negative quantity with 400")
    void shouldRejectNegativeAdjustment() throws Exception {
        String payload = """
                {
                    "quantity": -5
                }
                """;

        mockMvc.perform(put("/api/inventario/SKU-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
