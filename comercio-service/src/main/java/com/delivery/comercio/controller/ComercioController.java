package com.delivery.comercio.controller;

import com.delivery.comercio.dto.request.ComercioRequest;
import com.delivery.comercio.dto.request.ProductoRequest;
import com.delivery.comercio.dto.response.ComercioResponse;
import com.delivery.comercio.dto.response.ProductoResponse;
import com.delivery.comercio.model.enums.CategoriaComercio;
import com.delivery.comercio.service.IComercioService;
import com.delivery.comercio.service.IProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/comercios")
@RequiredArgsConstructor
public class ComercioController {

    private final IComercioService comercioService;
    private final IProductoService productoService;

    // GET /api/v1/comercios - Autenticado. Lista comercios activos con filtro opcional por categoría
    @GetMapping
    public ResponseEntity<List<ComercioResponse>> listarComercios(
            @RequestParam(required = false) CategoriaComercio categoria
    ) {
        List<ComercioResponse> comercios = comercioService.listarComercios(categoria);
        return ResponseEntity.ok(comercios);
    }

    // POST /api/v1/comercios - ADMIN. Registrar un nuevo comercio en la plataforma
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComercioResponse> registrarComercio(@Valid @RequestBody ComercioRequest request) {
        ComercioResponse response = comercioService.registrarComercio(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/v1/comercios/{id}/productos - Autenticado. Obtiene el menú/catálogo de productos de un comercio
    @GetMapping("/{id}/productos")
    public ResponseEntity<List<ProductoResponse>> listarProductosPorComercio(@PathVariable Long id) {
        List<ProductoResponse> productos = productoService.listarProductosPorComercio(id);
        return ResponseEntity.ok(productos);
    }

    // POST /api/v1/comercios/{id}/productos - ADMIN. Agregar un producto al catálogo de un comercio
    @PostMapping("/{id}/productos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoResponse> agregarProducto(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequest request
    ) {
        ProductoResponse response = productoService.agregarProducto(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
