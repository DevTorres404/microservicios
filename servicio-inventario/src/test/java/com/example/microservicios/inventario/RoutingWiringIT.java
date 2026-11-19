package com.example.microservicios.inventario;

import com.example.microservicios.inventario.domain.StockItem;
import com.example.microservicios.inventario.repository.StockRepository;
import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.ReplicaHealthRegistry;
import com.example.microservicios.persistence.routing.RoutingDecisionRecorder;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        healthRegistry.stopPeriodicProbing();
        healthRegistry.markHealthy(DataSourceKey.REPLICA_1);
        healthRegistry.markHealthy(DataSourceKey.REPLICA_2);
        stockRepository.save(new StockItem("SKU-RWIT-1", 50, new BigDecimal("15.50")));
    }

    @Test
    @DisplayName("W6-20: Verifies full routing wiring in servicio-inventario: replenishment -> PRIMARY, read -> REPLICA_*")
    void shouldRouteOperationsAccordingToContract() throws Exception {
        // 1. Write operation (PUT /api/inventario/{sku}) -> PRIMARY
        String adjustJson = """
                {
                    "quantity": 100,
                    "unitPrice": 15.50
                }
                """;

        mockMvc.perform(put("/api/inventario/SKU-RWIT-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustJson))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "PRIMARY"))
                .andExpect(jsonPath("$.sku").value("SKU-RWIT-1"))
                .andExpect(jsonPath("$.available").value(100));

        // 2. Ordinary read (GET /api/inventario) -> REPLICA_1 or REPLICA_2
        mockMvc.perform(get("/api/inventario")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("REPLICA_1"),
                        org.hamcrest.Matchers.is("REPLICA_2")
                )));

        // 3. Single item read (GET /api/inventario/{sku}) -> REPLICA_1 or REPLICA_2
        mockMvc.perform(get("/api/inventario/SKU-RWIT-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("REPLICA_1"),
                        org.hamcrest.Matchers.is("REPLICA_2")
                )));
    }
}
