package com.example.microservicios.inventario.web;

import com.example.microservicios.inventario.exception.InsufficientStockException;
import com.example.microservicios.inventario.exception.ReservationConflictException;
import com.example.microservicios.inventario.repository.StockRepository;
import com.example.microservicios.inventario.reservation.ReservationRequest;
import com.example.microservicios.inventario.reservation.ReservationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventarioController.class)
class InventarioErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockRepository stockRepository;

    @MockBean
    private ReservationService reservationService;

    @Test
    @DisplayName("400 Bad Request on bean validation failure contains RFC 9457 fields")
    void shouldReturn400OnValidationFailure() throws Exception {
        String payload = """
                {
                    "quantity": -10
                }
                """;

        mockMvc.perform(put("/api/inventario/SKU-1")
                        .header("X-Correlation-Id", "test-corr-400")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("test-corr-400"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @DisplayName("404 Not Found for non-existing SKU contains RFC 9457 fields")
    void shouldReturn404ForNonExistingSku() throws Exception {
        given(stockRepository.findById("MISSING-SKU")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/inventario/MISSING-SKU")
                        .header("X-Correlation-Id", "test-corr-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("SKU Not Found"))
                .andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("SKU not found: MISSING-SKU"))
                .andExpect(jsonPath("$.correlationId").value("test-corr-404"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("409 Conflict for insufficient stock reports missing SKUs")
    void shouldReturn409ForInsufficientStock() throws Exception {
        given(reservationService.reserve(any(ReservationRequest.class)))
                .willThrow(new InsufficientStockException(List.of("SKU-A", "SKU-B")));

        String payload = """
                {
                    "orderId": 55,
                    "lines": [
                        {"sku": "SKU-A", "quantity": 10},
                        {"sku": "SKU-B", "quantity": 20}
                    ]
                }
                """;

        mockMvc.perform(post("/api/inventario/reservas")
                        .header("X-Correlation-Id", "test-corr-409-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Insufficient Stock"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.missingSkus[0]").value("SKU-A"))
                .andExpect(jsonPath("$.missingSkus[1]").value("SKU-B"))
                .andExpect(jsonPath("$.correlationId").value("test-corr-409-stock"));
    }

    @Test
    @DisplayName("409 Conflict for reservation conflict contains RFC 9457 fields")
    void shouldReturn409ForReservationConflict() throws Exception {
        given(reservationService.reserve(any(ReservationRequest.class)))
                .willThrow(new ReservationConflictException("Different quantities for existing reservation"));

        String payload = """
                {
                    "orderId": 55,
                    "lines": [
                        {"sku": "SKU-A", "quantity": 10}
                    ]
                }
                """;

        mockMvc.perform(post("/api/inventario/reservas")
                        .header("X-Correlation-Id", "test-corr-409-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Reservation Conflict"))
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"))
                .andExpect(jsonPath("$.correlationId").value("test-corr-409-conflict"));
    }
}
