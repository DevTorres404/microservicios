package com.example.microservicios.pedidos.web;

import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.exception.InsufficientStockException;
import com.example.microservicios.pedidos.repository.OrderRepository;
import java.lang.reflect.Field;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
class PedidosErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @Test
    @DisplayName("400 Bad Request on empty lines does not persist order")
    void shouldReturn400OnEmptyLinesWithoutPersisting() throws Exception {
        String payload = """
                {
                    "customer": "Alice",
                    "lines": []
                }
                """;

        mockMvc.perform(post("/api/pedidos")
                        .header("X-Correlation-Id", "corr-empty-lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("corr-empty-lines"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.lines").exists());

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("400 Bad Request on quantity <= 0 does not persist order")
    void shouldReturn400OnNonPositiveQuantityWithoutPersisting() throws Exception {
        String payload = """
                {
                    "customer": "Alice",
                    "lines": [
                        {"sku": "SKU-1", "quantity": 0}
                    ]
                }
                """;

        mockMvc.perform(post("/api/pedidos")
                        .header("X-Correlation-Id", "corr-zero-qty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("corr-zero-qty"))
                .andExpect(jsonPath("$.errors['lines[0].quantity']").exists());

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("404 Not Found on missing order contains RFC 9457 fields")
    void shouldReturn404OnMissingOrder() throws Exception {
        given(orderRepository.findById(777L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/pedidos/777")
                        .header("X-Correlation-Id", "corr-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Order Not Found"))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Order not found: 777"))
                .andExpect(jsonPath("$.correlationId").value("corr-404"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("409 Conflict on prohibited state transition contains RFC 9457 fields")
    void shouldReturn409OnProhibitedStateTransition() throws Exception {
        Order order = Order.create("Alice", List.of(new OrderLineDraft("SKU-1", 1)));
        setOrderId(order, 10L);
        order.cancel();
        given(orderRepository.findById(10L)).willReturn(Optional.of(order));

        mockMvc.perform(post("/api/pedidos/10/cancelar")
                        .header("X-Correlation-Id", "corr-409-transition"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Illegal State Transition"))
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("Cannot transition from CANCELADO to CANCELADO"))
                .andExpect(jsonPath("$.correlationId").value("corr-409-transition"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private void setOrderId(Order order, Long id) {
        try {
            Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
