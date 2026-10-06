package com.delivery.pedido.dto.request;

import com.delivery.pedido.model.enums.EstadoPedido;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEstadoRequest {

    @NotNull(message = "El nuevo estado es obligatorio")
    private EstadoPedido nuevoEstado;

    private Long repartidorId; // Opcional, para asignarse el pedido
}
