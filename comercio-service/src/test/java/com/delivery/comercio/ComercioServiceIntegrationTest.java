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

    @Test
    @DisplayName("Debe manejar concurrencia estricta al descontar stock con hilos simultáneos")
    void testDescuentoStockConcurrente() throws InterruptedException {
        // Crear producto con stock 10
        ComercioResponse comResp = comercioService.listarComercios(null).get(0);
        ProductoRequest prodReq = ProductoRequest.builder()
                .nombre("Producto Concurrencia " + System.currentTimeMillis())
                .precio(new BigDecimal("25.00"))
                .stock(10)
                .disponible(true)
                .build();
        ProductoResponse prod = productoService.agregarProducto(comResp.getId(), prodReq);
        final Long prodId = prod.getId();

        int numHilos = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numHilos);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(numHilos);
        java.util.concurrent.atomic.AtomicInteger exitosos = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    productoService.descontarStock(prodId, 1);
                    exitosos.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // Liberar todos los hilos simultáneamente
        doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(10, exitosos.get(), "Los 10 hilos deben haber descontado 1 unidad exitosamente");
        ProductoResponse finalProd = productoService.obtenerProducto(prodId);
        assertEquals(0, finalProd.getStock(), "El stock final debe ser exactamente 0 tras las 10 deducciones");

        // Un intento adicional debe fallar por stock insuficiente
        assertThrows(InsufficientStockException.class, () -> {
            productoService.descontarStock(prodId, 1);
        });
    }
}
