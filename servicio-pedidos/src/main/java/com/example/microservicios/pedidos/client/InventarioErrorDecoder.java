package com.example.microservicios.pedidos.client;

import com.example.microservicios.pedidos.exception.InsufficientStockException;
import com.example.microservicios.pedidos.exception.InventarioUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventarioErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(InventarioErrorDecoder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 409) {
            List<String> missingSkus = extractMissingSkus(response);
            if (!missingSkus.isEmpty()) {
                return new InsufficientStockException(missingSkus);
            }
        }
        if (response.status() == 503) {
            return new InventarioUnavailableException("Inventory returned 503 Service Unavailable");
        }
        return defaultDecoder.decode(methodKey, response);
    }

    private List<String> extractMissingSkus(Response response) {
        List<String> skus = new ArrayList<>();
        if (response.body() == null) {
            return skus;
        }
        try (InputStream is = response.body().asInputStream()) {
            JsonNode root = MAPPER.readTree(is);
            JsonNode missingNode = root.get("missingSkus");
            if (missingNode != null && missingNode.isArray()) {
                for (JsonNode element : missingNode) {
                    skus.add(element.asText());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse missingSkus from 409 response: {}", e.getMessage());
        }
        return skus;
    }
}
