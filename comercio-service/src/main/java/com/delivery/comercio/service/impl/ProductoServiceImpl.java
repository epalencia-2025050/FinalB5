package com.delivery.comercio.service.impl;

import com.delivery.comercio.dto.request.ProductoRequest;
import com.delivery.comercio.dto.response.ProductoResponse;
import com.delivery.comercio.exception.InsufficientStockException;
import com.delivery.comercio.exception.ResourceNotFoundException;
import com.delivery.comercio.model.entity.Comercio;
import com.delivery.comercio.model.entity.Producto;
import com.delivery.comercio.repository.ComercioRepository;
import com.delivery.comercio.repository.ProductoRepository;
import com.delivery.comercio.service.IProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements IProductoService {

    private final ProductoRepository productoRepository;
    private final ComercioRepository comercioRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponse> listarProductosPorComercio(Long comercioId) {
        if (!comercioRepository.existsById(comercioId)) {
            throw new ResourceNotFoundException("Comercio no encontrado con ID: " + comercioId);
        }
        return productoRepository.findByComercioIdAndDisponibleTrue(comercioId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductoResponse agregarProducto(Long comercioId, ProductoRequest request) {
        Comercio comercio = comercioRepository.findById(comercioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado con ID: " + comercioId));

        Producto producto = Producto.builder()
                .comercio(comercio)
                .nombre(request.getNombre())
                .precio(request.getPrecio())
                .stock(request.getStock())
                .disponible(request.getDisponible() != null ? request.getDisponible() : true)
                .build();

        Producto saved = productoRepository.save(producto);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoResponse obtenerProducto(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        return mapToResponse(producto);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ProductoResponse descontarStock(Long id, int cantidad) {
        // Bloqueo pesimista para resistir ráfagas de pruebas de estrés concurrentes
        Producto producto = productoRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (!Boolean.TRUE.equals(producto.getDisponible())) {
            throw new InsufficientStockException("El producto '" + producto.getNombre() + "' no se encuentra disponible");
        }

        if (producto.getStock() < cantidad) {
            throw new InsufficientStockException("Stock insuficiente para '" + producto.getNombre()
                    + "'. Solicitado: " + cantidad + ", Disponible: " + producto.getStock());
        }

        producto.setStock(producto.getStock() - cantidad);
        Producto updated = productoRepository.save(producto);
        return mapToResponse(updated);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ProductoResponse restaurarStock(Long id, int cantidad) {
        Producto producto = productoRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        producto.setStock(producto.getStock() + cantidad);
        Producto updated = productoRepository.save(producto);
        return mapToResponse(updated);
    }

    private ProductoResponse mapToResponse(Producto p) {
        return ProductoResponse.builder()
                .id(p.getId())
                .comercioId(p.getComercio() != null ? p.getComercio().getId() : null)
                .nombre(p.getNombre())
                .precio(p.getPrecio())
                .stock(p.getStock())
                .disponible(p.getDisponible())
                .build();
    }
}
