package com.delivery.pedido.controller;

import com.delivery.pedido.dto.request.PedidoRequest;
import com.delivery.pedido.dto.request.UpdateEstadoRequest;
import com.delivery.pedido.dto.response.PedidoResponse;
import com.delivery.pedido.service.IPedidoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final IPedidoService pedidoService;

    // POST /api/v1/pedidos - CLIENTE
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<PedidoResponse> crearPedido(
            @Valid @RequestBody PedidoRequest request,
            HttpServletRequest servletRequest
    ) {
        Long clienteId = (Long) servletRequest.getAttribute("current_user_id");
        if (clienteId == null) {
            throw new AccessDeniedException("Token de autenticación no contiene un identificador de usuario válido");
        }
        PedidoResponse response = pedidoService.crearPedido(clienteId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/v1/pedidos/mis-pedidos - CLIENTE
    @GetMapping("/mis-pedidos")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<List<PedidoResponse>> listarMisPedidos(HttpServletRequest servletRequest) {
        Long clienteId = (Long) servletRequest.getAttribute("current_user_id");
        if (clienteId == null) {
            throw new AccessDeniedException("Token de autenticación no contiene un identificador de usuario válido");
        }
        List<PedidoResponse> pedidos = pedidoService.listarMisPedidos(clienteId);
        return ResponseEntity.ok(pedidos);
    }

    // GET /api/v1/pedidos/disponibles - REPARTIDOR, ADMIN
    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('REPARTIDOR', 'ADMIN')")
    public ResponseEntity<List<PedidoResponse>> listarPedidosDisponibles() {
        List<PedidoResponse> pedidos = pedidoService.listarPedidosDisponibles();
        return ResponseEntity.ok(pedidos);
    }

    // PATCH /api/v1/pedidos/{id}/estado - REPARTIDOR, ADMIN
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('REPARTIDOR', 'ADMIN')")
    public ResponseEntity<PedidoResponse> actualizarEstado(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateEstadoRequest request,
            HttpServletRequest servletRequest
    ) {
        Long usuarioId = (Long) servletRequest.getAttribute("current_user_id");
        if (usuarioId == null) {
            throw new AccessDeniedException("Token de autenticación no contiene un identificador de usuario válido");
        }
        String rol = (String) servletRequest.getAttribute("current_role");
        PedidoResponse response = pedidoService.actualizarEstado(id, request, usuarioId, rol);
        return ResponseEntity.ok(response);
    }

    // PATCH /api/v1/pedidos/{id}/cancelar o DELETE /api/v1/pedidos/{id} - CLIENTE, ADMIN
    @RequestMapping(value = {"/{id}/cancelar", "/{id}"}, method = {RequestMethod.PATCH, RequestMethod.DELETE})
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN')")
    public ResponseEntity<PedidoResponse> cancelarPedido(
            @PathVariable("id") Long id,
            HttpServletRequest servletRequest
    ) {
        Long usuarioId = (Long) servletRequest.getAttribute("current_user_id");
        if (usuarioId == null) {
            throw new AccessDeniedException("Token de autenticación no contiene un identificador de usuario válido");
        }
        String rol = (String) servletRequest.getAttribute("current_role");
        PedidoResponse response = pedidoService.cancelarPedido(id, usuarioId, rol);
        return ResponseEntity.ok(response);
    }
}
