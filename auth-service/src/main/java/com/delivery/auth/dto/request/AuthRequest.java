package com.delivery.auth.dto.request;

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
public class AuthRequest {
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato de email es invÃ¡lido")
    private String email;

    @NotBlank(message = "La contraseÃ±a es obligatoria")
    private String password;
}

