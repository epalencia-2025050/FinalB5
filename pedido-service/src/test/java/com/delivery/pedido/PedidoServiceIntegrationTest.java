package com.delivery.pedido;

import com.delivery.pedido.client.ComercioClient;
import com.delivery.pedido.client.ProductoInfo;
import com.delivery.pedido.dto.request.ItemPedidoRequest;
import com.delivery.pedido.dto.request.PedidoRequest;
import com.delivery.pedido.dto.request.UpdateEstadoRequest;
import com.delivery.pedido.dto.response.PedidoResponse;
import com.delivery.pedido.exception.InvalidStatusException;
import com.delivery.pedido.model.enums.EstadoPedido;
import com.delivery.pedido.service.IPedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
class PedidoServiceIntegrationTest {

    @Autowired
    private IPedidoService pedidoService;

    @MockBean
    private ComercioClient comercioClient;

    @BeforeEach
    void setupMock() {
        ProductoInfo mockProducto = ProductoInfo.builder()
                .id(1L)
                .nombre("Whopper Doble Combo")
                .precio(new BigDecimal("65.00"))
                .stock(50)
                .disponible(true)
                .build();

        Mockito.when(comercioClient.obtenerProducto(1L)).thenReturn(mockProducto);
        Mockito.when(comercioClient.descontarStock(eq(1L), anyInt())).thenReturn(mockProducto);
        Mockito.doNothing().when(comercioClient).restaurarStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Debe calcular consistentemente el total con Q20.00 de envío en el servidor")
    void testCalculoTotalYEnvio() {
        // Pedido de 2 combos de Q65.00 -> Subtotal Q130.00 + Q20.00 de envío fijo = Q150.00
        PedidoRequest request = PedidoRequest.builder()
                .items(List.of(
                        ItemPedidoRequest.builder().productoId(1L).cantidad(2).build()
                ))
                .build();

        PedidoResponse response = pedidoService.crearPedido(100L, request);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals(new BigDecimal("20.00"), response.getCostoEnvio());
        assertEquals(new BigDecimal("150.00"), response.getMontoTotal());
        assertEquals(EstadoPedido.PENDIENTE, response.getEstado());
        assertEquals(1, response.getDetalles().size());
        assertEquals(new BigDecimal("130.00"), response.getDetalles().get(0).getSubtotal());
    }

    @Test
    @DisplayName("Debe validar el flujo estricto de estados: PENDIENTE -> EN_PREPARACION -> EN_CAMINO -> ENTREGADO")
    void testFlujoEstrictoEstados() {
        PedidoRequest request = PedidoRequest.builder()
                .items(List.of(ItemPedidoRequest.builder().productoId(1L).cantidad(1).build()))
                .build();

        PedidoResponse creado = pedidoService.crearPedido(100L, request);
        Long pedidoId = creado.getId();

        // 1. Intentar saltar directamente a ENTREGADO desde PENDIENTE (debe fallar)
        assertThrows(InvalidStatusException.class, () -> {
            pedidoService.actualizarEstado(pedidoId,
                    UpdateEstadoRequest.builder().nuevoEstado(EstadoPedido.ENTREGADO).build(),
                    2L, "REPARTIDOR");
        });

        // 2. Transición válida 1: PENDIENTE -> EN_PREPARACION
        PedidoResponse paso1 = pedidoService.actualizarEstado(pedidoId,
                UpdateEstadoRequest.builder().nuevoEstado(EstadoPedido.EN_PREPARACION).build(),
                2L, "REPARTIDOR");
        assertEquals(EstadoPedido.EN_PREPARACION, paso1.getEstado());

        // 3. Transición válida 2: EN_PREPARACION -> EN_CAMINO
        PedidoResponse paso2 = pedidoService.actualizarEstado(pedidoId,
                UpdateEstadoRequest.builder().nuevoEstado(EstadoPedido.EN_CAMINO).build(),
                2L, "REPARTIDOR");
        assertEquals(EstadoPedido.EN_CAMINO, paso2.getEstado());

        // 4. Transición válida 3: EN_CAMINO -> ENTREGADO
        PedidoResponse paso3 = pedidoService.actualizarEstado(pedidoId,
                UpdateEstadoRequest.builder().nuevoEstado(EstadoPedido.ENTREGADO).build(),
                2L, "REPARTIDOR");
        assertEquals(EstadoPedido.ENTREGADO, paso3.getEstado());
    }

    @Test
    @DisplayName("Solo debe permitir cancelar si está en PENDIENTE y reponer stock")
    void testCancelacionSoloEnPendiente() {
        PedidoRequest request = PedidoRequest.builder()
                .items(List.of(ItemPedidoRequest.builder().productoId(1L).cantidad(1).build()))
                .build();

        PedidoResponse creado = pedidoService.crearPedido(100L, request);
        Long pedidoId = creado.getId();

        // Cancelar estando en PENDIENTE -> Debe permitirlo
        PedidoResponse cancelado = pedidoService.cancelarPedido(pedidoId, 100L, "CLIENTE");
        assertEquals(EstadoPedido.CANCELADO, cancelado.getEstado());

        // Crear otro pedido y avanzar a EN_PREPARACION
        PedidoResponse otro = pedidoService.crearPedido(100L, request);
        pedidoService.actualizarEstado(otro.getId(),
                UpdateEstadoRequest.builder().nuevoEstado(EstadoPedido.EN_PREPARACION).build(),
                2L, "REPARTIDOR");

        // Intentar cancelar en EN_PREPARACION -> Debe lanzar InvalidStatusException
        assertThrows(InvalidStatusException.class, () -> {
            pedidoService.cancelarPedido(otro.getId(), 100L, "CLIENTE");
        });
    }

    @Test
    @DisplayName("Debe ejecutar rollback compensatorio restaurando stock si falla un ítem posterior")
    void testRollbackCompensatorioStockInsuficiente() {
        ProductoInfo prod1 = ProductoInfo.builder().id(1L).nombre("P1").precio(new BigDecimal("10.00")).stock(5).disponible(true).build();
        ProductoInfo prod2 = ProductoInfo.builder().id(2L).nombre("P2").precio(new BigDecimal("20.00")).stock(0).disponible(true).build();

        Mockito.when(comercioClient.obtenerProducto(1L)).thenReturn(prod1);
        Mockito.when(comercioClient.obtenerProducto(2L)).thenReturn(prod2);
        Mockito.when(comercioClient.descontarStock(eq(1L), anyInt())).thenReturn(prod1);
        Mockito.when(comercioClient.descontarStock(eq(2L), anyInt())).thenThrow(new RuntimeException("Stock insuficiente"));

        PedidoRequest request = PedidoRequest.builder()
                .items(List.of(
                        ItemPedidoRequest.builder().productoId(1L).cantidad(2).build(),
                        ItemPedidoRequest.builder().productoId(2L).cantidad(1).build()
                ))
                .build();

        assertThrows(RuntimeException.class, () -> pedidoService.crearPedido(100L, request));

        // Verificar que se compensó restaurando el stock del producto 1
        Mockito.verify(comercioClient).restaurarStock(1L, 2);
    }

    @Test
    @DisplayName("Debe asegurar que en pedido multi-producto no queda stock descontado parcialmente")
    void testRollbackCompensatorioMultipleProductos() {
        ProductoInfo prod1 = ProductoInfo.builder().id(10L).nombre("P10").precio(new BigDecimal("10.00")).stock(5).disponible(true).build();
        ProductoInfo prod2 = ProductoInfo.builder().id(20L).nombre("P20").precio(new BigDecimal("15.00")).stock(5).disponible(true).build();
        ProductoInfo prod3 = ProductoInfo.builder().id(30L).nombre("P30").precio(new BigDecimal("20.00")).stock(0).disponible(true).build();

        Mockito.when(comercioClient.obtenerProducto(10L)).thenReturn(prod1);
        Mockito.when(comercioClient.obtenerProducto(20L)).thenReturn(prod2);
        Mockito.when(comercioClient.obtenerProducto(30L)).thenReturn(prod3);

        Mockito.when(comercioClient.descontarStock(eq(10L), eq(2))).thenReturn(prod1);
        Mockito.when(comercioClient.descontarStock(eq(20L), eq(3))).thenReturn(prod2);
        Mockito.when(comercioClient.descontarStock(eq(30L), eq(1))).thenThrow(new RuntimeException("Stock insuficiente en producto 30"));

        PedidoRequest request = PedidoRequest.builder()
                .items(List.of(
                        ItemPedidoRequest.builder().productoId(10L).cantidad(2).build(),
                        ItemPedidoRequest.builder().productoId(20L).cantidad(3).build(),
                        ItemPedidoRequest.builder().productoId(30L).cantidad(1).build()
                ))
                .build();

        assertThrows(RuntimeException.class, () -> pedidoService.crearPedido(100L, request));

        // Verificar que todos los productos previamente descontados fueron restaurados íntegramente
        Mockito.verify(comercioClient, Mockito.times(1)).restaurarStock(10L, 2);
        Mockito.verify(comercioClient, Mockito.times(1)).restaurarStock(20L, 3);
        // El producto 30 no se debe intentar restaurar porque nunca llegó a descontarse
        Mockito.verify(comercioClient, Mockito.never()).restaurarStock(eq(30L), anyInt());
    }

    @Test
    @DisplayName("Debe ejecutar rollback compensatorio si falla pedidoRepository.save() sin doble compensación")
    void testRollbackCompensatorioFalloEnSave() {
        com.delivery.pedido.repository.PedidoRepository mockRepo = Mockito.mock(com.delivery.pedido.repository.PedidoRepository.class);
        ComercioClient mockClient = Mockito.mock(ComercioClient.class);
        com.delivery.pedido.service.impl.PedidoServiceImpl service = new com.delivery.pedido.service.impl.PedidoServiceImpl(mockRepo, mockClient);

        ProductoInfo prod = ProductoInfo.builder().id(50L).nombre("Pizza").precio(new BigDecimal("50.00")).stock(10).disponible(true).build();
        Mockito.when(mockClient.obtenerProducto(50L)).thenReturn(prod);
        Mockito.when(mockClient.descontarStock(50L, 2)).thenReturn(prod);
        Mockito.when(mockRepo.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Fallo de base de datos simulado en save"));

        PedidoRequest request = PedidoRequest.builder()
                .items(List.of(ItemPedidoRequest.builder().productoId(50L).cantidad(2).build()))
                .build();

        // Debe preservar y propagar la excepción original sin silenciarla
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            service.crearPedido(99L, request);
        });

        // Debe compensar exactamente una vez (sin doble restauración)
        Mockito.verify(mockClient, Mockito.times(1)).restaurarStock(50L, 2);
    }
}
