package com.delivery.pedido.client;

import com.delivery.pedido.exception.InsufficientStockException;
import com.delivery.pedido.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ComercioClient {

    private final RestClient restClient;

    public ComercioClient(
            @Value("${services.comercio.url:http://localhost:8082}") String comercioUrl,
            @Value("${internal.api.secret:${INTERNAL_API_SECRET:delivery-internal-secret-token-key-2026}}") String internalSecret
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(comercioUrl)
                .defaultHeader("X-Internal-Secret", internalSecret)
                .build();
    }

    public ProductoInfo obtenerProducto(Long productoId) {
        try {
            return restClient.get()
                    .uri("/internal/productos/{id}", productoId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw new ResourceNotFoundException("Producto no encontrado con ID: " + productoId);
                    })
                    .body(ProductoInfo.class);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error comunicando con comercio-service: " + e.getMessage(), e);
        }
    }

    public ProductoInfo descontarStock(Long productoId, int cantidad) {
        try {
            return restClient.post()
                    .uri("/internal/productos/{id}/deduct-stock?cantidad={cantidad}", productoId, cantidad)
                    .retrieve()
                    .onStatus(status -> status.value() == 400, (req, resp) -> {
                        throw new InsufficientStockException("Stock insuficiente para producto con ID: " + productoId);
                    })
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw new ResourceNotFoundException("Producto no encontrado con ID: " + productoId);
                    })
                    .body(ProductoInfo.class);
        } catch (InsufficientStockException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error comunicando con comercio-service: " + e.getMessage(), e);
        }
    }

    public void restaurarStock(Long productoId, int cantidad) {
        try {
            restClient.post()
                    .uri("/internal/productos/{id}/restore-stock?cantidad={cantidad}", productoId, cantidad)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Error al restaurar stock para producto ID {}: {}", productoId, e.getMessage());
        }
    }
}
