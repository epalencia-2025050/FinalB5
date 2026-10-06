package com.delivery.pedido.service.impl;

import com.delivery.pedido.client.ComercioClient;
import com.delivery.pedido.client.ProductoInfo;
import com.delivery.pedido.dto.request.ItemPedidoRequest;
import com.delivery.pedido.dto.request.PedidoRequest;
import com.delivery.pedido.dto.request.UpdateEstadoRequest;
import com.delivery.pedido.dto.response.DetallePedidoResponse;
import com.delivery.pedido.dto.response.PedidoResponse;
import com.delivery.pedido.exception.InvalidStatusException;
import com.delivery.pedido.exception.ResourceNotFoundException;
import com.delivery.pedido.model.entity.DetallePedido;
import com.delivery.pedido.model.entity.Pedido;
import com.delivery.pedido.model.enums.EstadoPedido;
import com.delivery.pedido.repository.PedidoRepository;
import com.delivery.pedido.service.IPedidoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements IPedidoService {

    private static final BigDecimal COSTO_ENVIO_FIJO = new BigDecimal("20.00");

    private final PedidoRepository pedidoRepository;
    private final ComercioClient comercioClient;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PedidoResponse crearPedido(Long clienteId, PedidoRequest request) {
        log.info("Iniciando creación de pedido para cliente ID: {}", clienteId);

        BigDecimal subtotalAcumulado = BigDecimal.ZERO;
        List<DetallePedido> detalles = new ArrayList<>();
        List<ItemPedidoRequest> procesadosParaRollback = new ArrayList<>();

        try {
            for (ItemPedidoRequest item : request.getItems()) {
                // 1. Obtener precio actual y verificar disponibilidad en el comercio
                ProductoInfo producto = comercioClient.obtenerProducto(item.getProductoId());

                // 2. Descontar stock atómico (si no alcanza, lanza InsufficientStockException y detiene la transacción)
                comercioClient.descontarStock(item.getProductoId(), item.getCantidad());
                procesadosParaRollback.add(item);

                // 3. Cálculo de total consistente en servidor (precioActual * cantidad)
                BigDecimal subtotalItem = producto.getPrecio().multiply(BigDecimal.valueOf(item.getCantidad()));
                subtotalAcumulado = subtotalAcumulado.add(subtotalItem);

                DetallePedido detalle = DetallePedido.builder()
                        .productoId(item.getProductoId())
                        .cantidad(item.getCantidad())
                        .precioUnitario(producto.getPrecio())
                        .subtotal(subtotalItem)
                        .build();

                detalles.add(detalle);
            }
            // Total del pedido = Subtotal de productos + Costo fijo de envío (Q20.00)
            BigDecimal totalCalculado = subtotalAcumulado.add(COSTO_ENVIO_FIJO);

            Pedido pedido = Pedido.builder()
                    .clienteId(clienteId)
                    .fechaPedido(LocalDateTime.now())
                    .costoEnvio(COSTO_ENVIO_FIJO)
                    .montoTotal(totalCalculado)
                    .estado(EstadoPedido.PENDIENTE)
                    .build();

            for (DetallePedido d : detalles) {
                pedido.addDetalle(d);
            }

            Pedido guardado = pedidoRepository.save(pedido);
            log.info("Pedido creado con ID: {} y Total: Q{}", guardado.getId(), guardado.getMontoTotal());
            return mapToResponse(guardado);
        } catch (Exception ex) {
            log.warn("Fallo durante la creación del pedido. Iniciando compensación de stock. Causa: {}", ex.getMessage());
            // Rollback compensatorio para devolver el stock descontado en los ítems previos de esta orden
            for (ItemPedidoRequest revertir : procesadosParaRollback) {
                try {
                    comercioClient.restaurarStock(revertir.getProductoId(), revertir.getCantidad());
                    log.info("Stock compensado exitosamente para producto ID: {}, cantidad: {}", revertir.getProductoId(), revertir.getCantidad());
                } catch (Exception compEx) {
                    log.error("ALERTA CRÍTICA: Falló la compensación remota de stock para producto ID: {}, cantidad: {}. Requiere reconciliación manual. Causa: {}",
                            revertir.getProductoId(), revertir.getCantidad(), compEx.getMessage(), compEx);
                }
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponse> listarMisPedidos(Long clienteId) {
        return pedidoRepository.findByClienteIdOrderByFechaPedidoDesc(clienteId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidosDisponibles() {
        List<EstadoPedido> estadosVisibles = List.of(
                EstadoPedido.PENDIENTE,
                EstadoPedido.EN_PREPARACION,
                EstadoPedido.EN_CAMINO
        );
        return pedidoRepository.findByEstadoInOrderByFechaPedidoDesc(estadosVisibles).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PedidoResponse actualizarEstado(Long pedidoId, UpdateEstadoRequest request, Long usuarioId, String rol) {
        Pedido pedido = pedidoRepository.findByIdWithLock(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        EstadoPedido actual = pedido.getEstado();
        EstadoPedido nuevo = request.getNuevoEstado();

        // Validar flujo estricto: PENDIENTE -> EN_PREPARACION -> EN_CAMINO -> ENTREGADO
        boolean transicionValida = switch (actual) {
            case PENDIENTE -> nuevo == EstadoPedido.EN_PREPARACION;
            case EN_PREPARACION -> nuevo == EstadoPedido.EN_CAMINO;
            case EN_CAMINO -> nuevo == EstadoPedido.ENTREGADO;
            default -> false;
        };

        if (!transicionValida) {
            throw new InvalidStatusException("Transición inválida de '" + actual + "' hacia '" + nuevo
                    + "'. El flujo estricto es: PENDIENTE -> EN_PREPARACION -> EN_CAMINO -> ENTREGADO.");
        }

        pedido.setEstado(nuevo);

        // Si es repartidor o se proporciona repartidorId, registrar asignación
        if (request.getRepartidorId() != null) {
            pedido.setRepartidorId(request.getRepartidorId());
        } else if ("REPARTIDOR".equals(rol) && pedido.getRepartidorId() == null) {
            pedido.setRepartidorId(usuarioId);
        }

        Pedido actualizado = pedidoRepository.save(pedido);
        return mapToResponse(actualizado);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PedidoResponse cancelarPedido(Long pedidoId, Long usuarioId, String rol) {
        Pedido pedido = pedidoRepository.findByIdWithLock(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        // Regla: Solo se puede cancelar si está en estado PENDIENTE
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new InvalidStatusException("Un pedido solo puede cancelarse si está en estado PENDIENTE. Estado actual: " + pedido.getEstado());
        }

        // Si es CLIENTE, verificar que el pedido le pertenezca
        if ("CLIENTE".equals(rol) && !pedido.getClienteId().equals(usuarioId)) {
            throw new AccessDeniedException("No tiene permisos para cancelar un pedido ajeno.");
        }

        pedido.setEstado(EstadoPedido.CANCELADO);

        // Restaurar el stock de cada producto involucrado
        for (DetallePedido detalle : pedido.getDetalles()) {
            try {
                comercioClient.restaurarStock(detalle.getProductoId(), detalle.getCantidad());
            } catch (Exception compEx) {
                log.error("ALERTA CRÍTICA: Falló la restauración de stock para producto ID: {} en cancelación de pedido {}. Requiere reconciliación manual. Causa: {}",
                        detalle.getProductoId(), pedidoId, compEx.getMessage(), compEx);
            }
        }

        Pedido cancelado = pedidoRepository.save(pedido);
        log.info("Pedido {} cancelado y stock restaurado exitosamente.", pedidoId);
        return mapToResponse(cancelado);
    }

    private PedidoResponse mapToResponse(Pedido p) {
        List<DetallePedidoResponse> detalles = p.getDetalles().stream()
                .map(d -> DetallePedidoResponse.builder()
                        .id(d.getId())
                        .productoId(d.getProductoId())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        .build())
                .toList();

        return PedidoResponse.builder()
                .id(p.getId())
                .clienteId(p.getClienteId())
                .repartidorId(p.getRepartidorId())
                .fechaPedido(p.getFechaPedido())
                .costoEnvio(p.getCostoEnvio())
                .montoTotal(p.getMontoTotal())
                .estado(p.getEstado())
                .detalles(detalles)
                .build();
    }
}
