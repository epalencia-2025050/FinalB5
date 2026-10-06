package com.delivery.comercio.service;

import com.delivery.comercio.dto.request.ProductoRequest;
import com.delivery.comercio.dto.response.ProductoResponse;

import java.util.List;

public interface IProductoService {
    List<ProductoResponse> listarProductosPorComercio(Long comercioId);
    ProductoResponse agregarProducto(Long comercioId, ProductoRequest request);
    ProductoResponse obtenerProducto(Long id);
    ProductoResponse descontarStock(Long id, int cantidad);
    ProductoResponse restaurarStock(Long id, int cantidad);
}
