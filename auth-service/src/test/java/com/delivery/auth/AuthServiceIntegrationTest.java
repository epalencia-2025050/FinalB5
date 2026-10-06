package com.delivery.auth;

import com.delivery.auth.dto.request.AuthRequest;
import com.delivery.auth.dto.request.RegisterRequest;
import com.delivery.auth.dto.response.AuthResponse;
import com.delivery.auth.model.enums.Rol;
import com.delivery.auth.service.IAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthServiceIntegrationTest {

    @Autowired
    private IAuthService authService;

    @Test
    @DisplayName("Debe autenticar al ADMIN inicial y generar JWT válido")
    void testLoginAdmin() {
        AuthRequest loginRequest = AuthRequest.builder()
                .email("admin@delivery.com")
                .password("admin123")
                .build();

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals("ADMIN", response.getRol());
        assertEquals("admin@delivery.com", response.getEmail());
    }

    @Test
    @DisplayName("Debe registrar un nuevo usuario con rol CLIENTE y generar JWT")
    void testRegisterCliente() {
        String randomEmail = "nuevo_cliente_" + System.currentTimeMillis() + "@delivery.com";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .nombre("Juan Perez")
                .direccion("Zona 1, Ciudad")
                .telefono("44445555")
                .email(randomEmail)
                .password("password123")
                .build();

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals(Rol.CLIENTE.name(), response.getRol());
        assertEquals(randomEmail, response.getEmail());
    }
}
