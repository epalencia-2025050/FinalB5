package com.delivery.pedido.dto.response;

import com.delivery.pedido.model.enums.EstadoPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoResponse {
    private Long id;
    private Long clienteId;
    private Long repartidorId;
    private LocalDateTime fechaPedido;
    private BigDecimal costoEnvio;
    private BigDecimal montoTotal;
    private EstadoPedido estado;
    private List<DetallePedidoResponse> detalles;
}
