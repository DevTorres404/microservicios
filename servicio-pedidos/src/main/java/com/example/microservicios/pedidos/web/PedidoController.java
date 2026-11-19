package com.example.microservicios.pedidos.web;

import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.exception.OrderNotFoundException;
import com.example.microservicios.pedidos.repository.OrderRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final OrderRepository orderRepository;

    public PedidoController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = Order.create(request.customer(), request.toDrafts());
        Order saved = orderRepository.save(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(saved));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<OrderResponse>> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderStatus estado) {
        OrderStatus effectiveStatus = status != null ? status : estado;
        List<Order> orders = effectiveStatus != null
                ? orderRepository.findByStatusWithLines(effectiveStatus)
                : orderRepository.findAllWithLines();
        List<OrderResponse> response = orders.stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancelar")
    @Transactional
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.cancel();
        Order saved = orderRepository.save(order);
        return ResponseEntity.ok(OrderResponse.from(saved));
    }
}
