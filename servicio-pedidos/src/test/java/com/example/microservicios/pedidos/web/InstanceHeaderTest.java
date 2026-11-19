package com.example.microservicios.pedidos.web;

import com.example.microservicios.pedidos.repository.OrderRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
@Import(InstanceHeaderFilter.class)
class InstanceHeaderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Echoes incoming X-Correlation-Id and stamps X-Service-Instance")
    void shouldEchoCorrelationIdAndStampInstance() throws Exception {
        given(orderRepository.findAllWithLines()).willReturn(List.of());

        mockMvc.perform(get("/api/pedidos")
                        .header("X-Correlation-Id", "my-custom-corr-id-pedidos"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "my-custom-corr-id-pedidos"))
                .andExpect(header().string("X-Service-Instance", notNullValue()));
    }

    @Test
    @DisplayName("Generates new X-Correlation-Id when absent in request")
    void shouldGenerateCorrelationIdWhenMissing() throws Exception {
        given(orderRepository.findAllWithLines()).willReturn(List.of());

        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id",
                        matchesPattern("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")))
                .andExpect(header().string("X-Service-Instance", notNullValue()));
    }
}
