package com.delivery.comercio.dto.request;

import com.delivery.comercio.model.enums.CategoriaComercio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComercioRequest {

    @NotBlank(message = "El nombre del comercio es obligatorio")
    private String nombre;

    @NotNull(message = "La categoría es obligatoria (RESTAURANTE, SUPERMERCADO, FARMACIA)")
    private CategoriaComercio categoria;

    @NotBlank(message = "La dirección es obligatoria")
    private String direccion;

    @Builder.Default
    private Boolean abierto = true;
}
