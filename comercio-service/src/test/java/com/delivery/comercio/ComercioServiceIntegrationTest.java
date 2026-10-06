package com.delivery.comercio;

import com.delivery.comercio.dto.request.ComercioRequest;
import com.delivery.comercio.dto.request.ProductoRequest;
import com.delivery.comercio.dto.response.ComercioResponse;
import com.delivery.comercio.dto.response.ProductoResponse;
import com.delivery.comercio.exception.InsufficientStockException;
import com.delivery.comercio.model.enums.CategoriaComercio;
import com.delivery.comercio.service.IComercioService;
import com.delivery.comercio.service.IProductoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ComercioServiceIntegrationTest {

    @Autowired
    private IComercioService comercioService;

    @Autowired
    private IProductoService productoService;

    @Test
    @DisplayName("Debe listar comercios activos y permitir filtro por categoría")
    void testListarComercios() {
        List<ComercioResponse> todos = comercioService.listarComercios(null);
        assertNotNull(todos);
        assertFalse(todos.isEmpty(), "Debe haber al menos 1 comercio semilla creado");

        List<ComercioResponse> restaurantes = comercioService.listarComercios(CategoriaComercio.RESTAURANTE);
        assertNotNull(restaurantes);
        assertTrue(restaurantes.stream().allMatch(c -> c.getCategoria() == CategoriaComercio.RESTAURANTE));
    }

    @Test
    @DisplayName("Debe registrar un nuevo comercio y agregar un producto")
    void testRegistrarComercioYProducto() {
        ComercioRequest comReq = ComercioRequest.builder()
                .nombre("Farmacia Galeno")
                .categoria(CategoriaComercio.FARMACIA)
                .direccion("Zona 9, Ciudad")
                .abierto(true)
                .build();

        ComercioResponse comResp = comercioService.registrarComercio(comReq);
        assertNotNull(comResp.getId());

        ProductoRequest prodReq = ProductoRequest.builder()
                .nombre("Paracetamol 500mg")
                .precio(new BigDecimal("15.00"))
                .stock(20)
                .disponible(true)
                .build();

        ProductoResponse prodResp = productoService.agregarProducto(comResp.getId(), prodReq);
        assertNotNull(prodResp.getId());
        assertEquals(20, prodResp.getStock());

        // Probar descuento atómico de stock
        ProductoResponse descontado = productoService.descontarStock(prodResp.getId(), 5);
        assertEquals(15, descontado.getStock());

        // Probar que lanzar más de lo disponible arroje InsufficientStockException
        assertThrows(InsufficientStockException.class, () -> {
            productoService.descontarStock(prodResp.getId(), 50);
        });
    }
}
