package com.delivery.pedido.service;

import com.delivery.pedido.dto.request.PedidoRequest;
import com.delivery.pedido.dto.request.UpdateEstadoRequest;
import com.delivery.pedido.dto.response.PedidoResponse;

import java.util.List;

public interface IPedidoService {
    PedidoResponse crearPedido(Long clienteId, PedidoRequest request);
    List<PedidoResponse> listarMisPedidos(Long clienteId);
    List<PedidoResponse> listarPedidosDisponibles();
    PedidoResponse actualizarEstado(Long pedidoId, UpdateEstadoRequest request, Long usuarioId, String rol);
    PedidoResponse cancelarPedido(Long pedidoId, Long usuarioId, String rol);
}
