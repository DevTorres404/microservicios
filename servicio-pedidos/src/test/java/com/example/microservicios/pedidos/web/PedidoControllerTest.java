package com.example.microservicios.pedidos.web;

import com.example.microservicios.pedidos.domain.Money;
import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.repository.OrderRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @Test
    @DisplayName("POST /api/pedidos creates order in PENDIENTE state")
    void shouldCreateOrder() throws Exception {
        Order order = Order.create("Alice", List.of(new OrderLineDraft("SKU-1", 2)));
        setOrderId(order, 1L);
        given(orderRepository.save(any(Order.class))).willReturn(order);

        String payload = """
                {
                    "customer": "Alice",
                    "lines": [
                        {"sku": "SKU-1", "quantity": 2}
                    ]
                }
                """;

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customer").value("Alice"))
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.lines[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
    }

    @Test
    @DisplayName("POST /api/pedidos rejects empty lines with 400")
    void shouldRejectEmptyLines() throws Exception {
        String payload = """
                {
                    "customer": "Alice",
                    "lines": []
                }
                """;

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/pedidos rejects quantity <= 0 with 400")
    void shouldRejectNonPositiveQuantity() throws Exception {
        String payload = """
                {
                    "customer": "Alice",
                    "lines": [
                        {"sku": "SKU-1", "quantity": 0}
                    ]
                }
                """;

        mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/pedidos/{id} returns order when found")
    void shouldReturnOrderById() throws Exception {
        Order order = Order.create("Bob", List.of(new OrderLineDraft("SKU-2", 3)));
        setOrderId(order, 42L);
        order.applyInventoryPrices(Map.of("SKU-2", Money.of("15.00")));
        given(orderRepository.findById(42L)).willReturn(Optional.of(order));

        mockMvc.perform(get("/api/pedidos/42").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.customer").value("Bob"))
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").value(45.00))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(15.00));
    }

    @Test
    @DisplayName("GET /api/pedidos/{id} returns 404 ProblemDetail when not found")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/pedidos/999").accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/pedidos lists orders")
    void shouldListOrders() throws Exception {
        Order order1 = Order.create("Alice", List.of(new OrderLineDraft("SKU-1", 1)));
        setOrderId(order1, 1L);
        Order order2 = Order.create("Bob", List.of(new OrderLineDraft("SKU-2", 2)));
        setOrderId(order2, 2L);
        given(orderRepository.findAllWithLines()).willReturn(List.of(order1, order2));

        mockMvc.perform(get("/api/pedidos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @DisplayName("GET /api/pedidos with status filters orders")
    void shouldFilterOrdersByStatus() throws Exception {
        Order order = Order.create("Alice", List.of(new OrderLineDraft("SKU-1", 1)));
        setOrderId(order, 1L);
        order.confirm();
        given(orderRepository.findByStatusWithLines(OrderStatus.CONFIRMADO)).willReturn(List.of(order));

        mockMvc.perform(get("/api/pedidos?status=CONFIRMADO").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMADO"));
    }

    @Test
    @DisplayName("POST /api/pedidos/{id}/cancelar cancels an order")
    void shouldCancelOrder() throws Exception {
        Order order = Order.create("Alice", List.of(new OrderLineDraft("SKU-1", 1)));
        setOrderId(order, 1L);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/pedidos/1/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    @DisplayName("POST /api/pedidos/{id}/cancelar returns 409 when already cancelled")
    void shouldReturn409WhenCancellingAlreadyCancelledOrder() throws Exception {
        Order order = Order.create("Alice", List.of(new OrderLineDraft("SKU-1", 1)));
        setOrderId(order, 1L);
        order.cancel();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        mockMvc.perform(post("/api/pedidos/1/cancelar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
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
