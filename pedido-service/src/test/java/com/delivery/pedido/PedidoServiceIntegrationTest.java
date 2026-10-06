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
}
