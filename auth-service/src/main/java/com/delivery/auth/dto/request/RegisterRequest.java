package com.delivery.auth.dto.request;

import com.delivery.auth.model.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String direccion;

    private String telefono;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato de email no es vÃ¡lido")
    private String email;

    @NotBlank(message = "La contraseÃ±a es obligatoria")
    private String password;

    private Rol rol; // Opcional, si no viene se asigna CLIENTE
}

