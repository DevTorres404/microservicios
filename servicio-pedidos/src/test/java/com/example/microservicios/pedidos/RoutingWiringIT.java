package com.example.microservicios.pedidos;

import com.example.microservicios.pedidos.client.InventarioClient;
import com.example.microservicios.pedidos.client.ReservationResponseDto;
import com.example.microservicios.pedidos.client.ReservedLineDto;
import com.example.microservicios.pedidos.repository.OrderRepository;
import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.ReplicaHealthRegistry;
import com.example.microservicios.persistence.routing.RoutingDecisionRecorder;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoutingWiringIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReplicaHealthRegistry healthRegistry;

    @Autowired
    private RoutingDecisionRecorder decisionRecorder;

    @MockitoBean
    private InventarioClient inventarioClient;

    @BeforeEach
    void setUp() {
        healthRegistry.stopPeriodicProbing();
        healthRegistry.markHealthy(DataSourceKey.REPLICA_1);
        healthRegistry.markHealthy(DataSourceKey.REPLICA_2);
    }

    @Test
    @DisplayName("W6-20: Verifies full routing wiring with Testcontainers: write -> PRIMARY, critical read -> PRIMARY, ordinary read -> REPLICA_*")
    void shouldRouteOperationsAccordingToContract() throws Exception {
        // Setup inventory mock for order creation
        Mockito.when(inventarioClient.reserve(Mockito.any())).thenReturn(
                new ReservationResponseDto(1L, List.of(
                        new ReservedLineDto("SKU-W6-1", 2, new BigDecimal("25.00"))
                ), new BigDecimal("50.00"), false)
        );

        // 1. Write operation (POST /api/pedidos) -> PRIMARY
        String createJson = """
                {
                    "customer": "John Doe",
                    "lines": [
                        { "sku": "SKU-W6-1", "quantity": 2 }
                    ]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Data-Source", "PRIMARY"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Number idNum = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");
        long orderId = idNum.longValue();

        // 2. Critical read (@CriticalRead on GET /api/pedidos/{id}) -> PRIMARY
        mockMvc.perform(get("/api/pedidos/" + orderId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "PRIMARY"))
                .andExpect(jsonPath("$.customer").value("John Doe"));

        // 3. Ordinary read (GET /api/pedidos) -> REPLICA_1 or REPLICA_2
        mockMvc.perform(get("/api/pedidos")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("REPLICA_1"),
                        org.hamcrest.Matchers.is("REPLICA_2")
                )));
    }
}
