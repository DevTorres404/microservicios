package com.example.microservicios.inventario.web;

import com.example.microservicios.inventario.domain.StockItem;
import com.example.microservicios.inventario.exception.SkuNotFoundException;
import com.example.microservicios.inventario.repository.StockRepository;
import com.example.microservicios.inventario.reservation.ReservationRequest;
import com.example.microservicios.inventario.reservation.ReservationResult;
import com.example.microservicios.inventario.reservation.ReservationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final StockRepository stockRepository;
    private final ReservationService reservationService;

    public InventarioController(StockRepository stockRepository,
                                ReservationService reservationService) {
        this.stockRepository = stockRepository;
        this.reservationService = reservationService;
    }

    @GetMapping
    public List<StockResponse> listStock() {
        return stockRepository.findAllByOrderBySkuAsc().stream()
                .map(StockResponse::from)
                .toList();
    }

    @GetMapping("/{sku}")
    public ResponseEntity<StockResponse> getStock(@PathVariable String sku) {
        return stockRepository.findById(sku)
                .map(StockResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new SkuNotFoundException(sku));
    }

    @PostMapping("/reservas")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        ReservationResult result = reservationService.reserve(request);
        return ResponseEntity.ok(ReservationResponse.from(result));
    }

    @PostMapping("/reservas/{pedidoId}/liberacion")
    public ResponseEntity<Map<String, Object>> release(@PathVariable long pedidoId) {
        boolean released = reservationService.release(pedidoId);
        return ResponseEntity.ok(Map.of("orderId", pedidoId, "released", released));
    }

    @PutMapping("/{sku}")
    @Transactional
    public ResponseEntity<StockResponse> adjustStock(@PathVariable String sku,
                                                    @Valid @RequestBody StockAdjustmentRequest request) {
        StockItem item = stockRepository.findById(sku)
                .orElseThrow(() -> new SkuNotFoundException(sku));
        item.updateAvailable(request.quantity());
        stockRepository.save(item);
        return ResponseEntity.ok(StockResponse.from(item));
    }
}
