package com.example.microservicios.pedidos.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.microservicios.pedidos.client.InventarioClient;
import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.repository.OrderRepository;
import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.RoutingDecision;
import com.example.microservicios.persistence.routing.RoutingDecisionRecorder;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
@Import({InstanceHeaderFilter.class, PedidosExceptionHandler.class})
class RoutingLogTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private InventarioClient inventarioClient;

    @MockitoBean
    private RoutingDecisionRecorder decisionRecorder;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(InstanceHeaderFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("Emits X-Data-Source header and structured log line with routingKey for read requests")
    void shouldEmitHeaderAndStructuredLogForRead() throws Exception {
        Mockito.when(decisionRecorder.lastDecision()).thenReturn(DataSourceKey.REPLICA_1);
        Mockito.when(decisionRecorder.lastDecisionContext()).thenReturn(
                new RoutingDecision(DataSourceKey.REPLICA_1, true, false)
        );
        Mockito.when(orderRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/pedidos")
                        .header("X-Correlation-Id", "test-corr-read")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "REPLICA_1"));

        // Check structured log
        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(logMessages).anyMatch(msg ->
                msg.contains("correlationId=test-corr-read") &&
                msg.contains("routingKey=REPLICA_1") &&
                msg.contains("txReadOnly=true") &&
                msg.contains("criticalRead=false") &&
                msg.contains("outcome=SUCCESS")
        );
    }

    @Test
    @DisplayName("Emits X-Data-Source header and structured log line for write requests (PRIMARY)")
    void shouldEmitHeaderAndStructuredLogForWrite() throws Exception {
        Mockito.when(decisionRecorder.lastDecision()).thenReturn(DataSourceKey.PRIMARY);
        Mockito.when(decisionRecorder.lastDecisionContext()).thenReturn(
                new RoutingDecision(DataSourceKey.PRIMARY, false, false)
        );

        Order saved = Order.create("customer-1", List.of(new OrderLineDraft("SKU-001", 2)));
        setOrderId(saved, 1L);
        Mockito.when(orderRepository.save(Mockito.any())).thenReturn(saved);

        String orderJson = """
                {
                    "customer": "customer-1",
                    "lines": [
                        { "sku": "SKU-001", "quantity": 2 }
                    ]
                }
                """;

        mockMvc.perform(post("/api/pedidos")
                        .header("X-Correlation-Id", "test-corr-write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Data-Source", "PRIMARY"));

        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(logMessages).anyMatch(msg ->
                msg.contains("correlationId=test-corr-write") &&
                msg.contains("routingKey=PRIMARY") &&
                msg.contains("txReadOnly=false") &&
                msg.contains("criticalRead=false") &&
                msg.contains("outcome=SUCCESS")
        );
    }

    @Test
    @DisplayName("Emits X-Data-Source: PRIMARY and criticalRead=true log for CriticalRead requests")
    void shouldEmitHeaderAndLogForCriticalRead() throws Exception {
        Mockito.when(decisionRecorder.lastDecision()).thenReturn(DataSourceKey.PRIMARY);
        Mockito.when(decisionRecorder.lastDecisionContext()).thenReturn(
                new RoutingDecision(DataSourceKey.PRIMARY, true, true)
        );

        Order order = Order.create("customer-1", List.of(new OrderLineDraft("SKU-001", 2)));
        setOrderId(order, 42L);
        Mockito.when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/pedidos/42")
                        .header("X-Correlation-Id", "test-corr-critical")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "PRIMARY"));

        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(logMessages).anyMatch(msg ->
                msg.contains("correlationId=test-corr-critical") &&
                msg.contains("routingKey=PRIMARY") &&
                msg.contains("criticalRead=true") &&
                msg.contains("outcome=SUCCESS")
        );
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
