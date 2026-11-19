package com.example.microservicios.pedidos.service;

import com.example.microservicios.pedidos.client.InventarioClient;
import com.example.microservicios.pedidos.client.ReservationLineDto;
import com.example.microservicios.pedidos.client.ReservationRequestDto;
import com.example.microservicios.pedidos.client.ReservationResponseDto;
import com.example.microservicios.pedidos.client.ReservedLineDto;
import com.example.microservicios.pedidos.domain.Money;
import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.exception.DomainException;
import com.example.microservicios.pedidos.exception.InsufficientStockException;
import com.example.microservicios.pedidos.exception.InventarioUnavailableException;
import com.example.microservicios.pedidos.exception.OrderNotFoundException;
import com.example.microservicios.pedidos.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventarioClient inventarioClient;
    private final MeterRegistry meterRegistry;

    public OrderService(OrderRepository orderRepository,
                        InventarioClient inventarioClient,
                        @Autowired(required = false) MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.inventarioClient = inventarioClient;
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    /**
     * Orchestrates order creation across two local transactions and a remote Feign call:
     * 1. Tx1: Inserts the order in PENDIENTE state.
     * 2. Remote call: Invokes inventory reservation.
     * 3. Tx2:
     *    - On success: applies inventory prices and transitions order to CONFIRMADO.
     *    - On insufficient stock (409): transitions order to RECHAZADO and throws InsufficientStockException.
     *    - On failure / circuit breaker: order remains in PENDIENTE, best-effort compensation is triggered.
     */
    public Order createOrder(String customer, List<OrderLineDraft> drafts) {
        Order order = savePending(customer, drafts);
        Long orderId = order.getId() != null ? order.getId() : 0L;

        List<ReservationLineDto> lines = drafts.stream()
                .map(d -> new ReservationLineDto(d.sku(), d.quantity()))
                .toList();
        ReservationRequestDto request = new ReservationRequestDto(orderId, lines);

        ReservationResponseDto reservation;
        try {
            reservation = inventarioClient.reserve(request);
        } catch (InsufficientStockException ex) {
            rejectOrder(order);
            throw ex;
        } catch (Exception ex) {
            compensateReservation(orderId);
            if (ex instanceof DomainException de) {
                throw de;
            }
            throw new InventarioUnavailableException("Failed to reserve inventory for order " + orderId, ex);
        }

        Map<String, Money> prices = new HashMap<>();
        for (ReservedLineDto line : reservation.lines()) {
            prices.put(line.sku(), Money.of(line.unitPrice()));
        }
        return confirmOrder(order, prices);
    }

    /**
     * Cancels an order and triggers best-effort compensatory release on inventory.
     */
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.cancel();
        Order saved = orderRepository.save(order);

        compensateReservation(orderId);

        return saved;
    }

    @Transactional
    public Order savePending(String customer, List<OrderLineDraft> drafts) {
        Order order = Order.create(customer, drafts);
        return orderRepository.save(order);
    }

    @Transactional
    public Order confirmOrder(Order order, Map<String, Money> prices) {
        Order current = order.getId() != null
                ? orderRepository.findById(order.getId()).orElse(order)
                : order;
        current.applyInventoryPrices(prices);
        current.confirm();
        return orderRepository.save(current);
    }

    @Transactional
    public void rejectOrder(Order order) {
        Order current = order.getId() != null
                ? orderRepository.findById(order.getId()).orElse(order)
                : order;
        current.reject();
        orderRepository.save(current);
    }

    private void compensateReservation(long orderId) {
        try {
            inventarioClient.release(orderId);
        } catch (Exception ex) {
            log.warn("Best-effort compensation release failed for order {}: {}", orderId, ex.getMessage());
            if (meterRegistry != null) {
                meterRegistry.counter("compensation_failures_total", "service", "servicio-pedidos").increment();
            }
        }
    }
}
