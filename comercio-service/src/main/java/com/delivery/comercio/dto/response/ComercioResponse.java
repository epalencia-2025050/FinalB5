package com.delivery.comercio.dto.response;

import com.delivery.comercio.model.enums.CategoriaComercio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComercioResponse {
    private Long id;
    private String nombre;
    private CategoriaComercio categoria;
    private String direccion;
    private Boolean abierto;
}
