package com.delivery.comercio.controller;

import com.delivery.comercio.dto.response.ProductoResponse;
import com.delivery.comercio.service.IProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/productos")
@RequiredArgsConstructor
public class InternalProductoController {

    private final IProductoService productoService;

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> obtenerProducto(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerProducto(id));
    }

    @PostMapping("/{id}/deduct-stock")
    public ResponseEntity<ProductoResponse> descontarStock(
            @PathVariable Long id,
            @RequestParam int cantidad
    ) {
        return ResponseEntity.ok(productoService.descontarStock(id, cantidad));
    }

    @PostMapping("/{id}/restore-stock")
    public ResponseEntity<ProductoResponse> restaurarStock(
            @PathVariable Long id,
            @RequestParam int cantidad
    ) {
        return ResponseEntity.ok(productoService.restaurarStock(id, cantidad));
    }
}
