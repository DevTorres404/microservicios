package com.example.microservicios.inventario.web;

import com.example.microservicios.inventario.domain.StockItem;
import com.example.microservicios.inventario.repository.StockRepository;
import com.example.microservicios.inventario.reservation.ReservationService;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventarioController.class)
class InventarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockRepository stockRepository;

    @MockBean
    private ReservationService reservationService;

    @Test
    @DisplayName("GET /api/inventario returns all available stock items")
    void shouldListAllStockItems() throws Exception {
        given(stockRepository.findAllByOrderBySkuAsc()).willReturn(List.of(
                new StockItem("SKU-1", 10, new BigDecimal("19.99")),
                new StockItem("SKU-2", 5, new BigDecimal("9.50"))
        ));

        mockMvc.perform(get("/api/inventario").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$[0].available").value(10))
                .andExpect(jsonPath("$[0].unitPrice").value(19.99))
                .andExpect(jsonPath("$[1].sku").value("SKU-2"))
                .andExpect(jsonPath("$[1].available").value(5))
                .andExpect(jsonPath("$[1].unitPrice").value(9.50));
    }

    @Test
    @DisplayName("GET /api/inventario/{sku} returns stock item when found")
    void shouldReturnStockItemWhenFound() throws Exception {
        given(stockRepository.findById("SKU-1")).willReturn(
                Optional.of(new StockItem("SKU-1", 10, new BigDecimal("19.99")))
        );

        mockMvc.perform(get("/api/inventario/SKU-1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.available").value(10))
                .andExpect(jsonPath("$.unitPrice").value(19.99));
    }

    @Test
    @DisplayName("GET /api/inventario/{sku} returns 404 ProblemDetail when not found")
    void shouldReturn404WhenSkuNotFound() throws Exception {
        given(stockRepository.findById("UNKNOWN")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/inventario/UNKNOWN").accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
    }
}
