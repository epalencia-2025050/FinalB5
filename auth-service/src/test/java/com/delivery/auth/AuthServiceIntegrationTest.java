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

    @Test
    @DisplayName("Debe prevenir escalamiento de privilegios: solicitud con ADMIN debe registrar CLIENTE")
    void testRegisterPreventsPrivilegeEscalation() {
        String randomEmail = "intento_admin_" + System.currentTimeMillis() + "@delivery.com";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .nombre("Hacker Wannabe")
                .direccion("Desconocida")
                .telefono("11112222")
                .email(randomEmail)
                .password("hackerpass")
                .rol(Rol.ADMIN) // Intento de escalar privilegios
                .build();

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals(Rol.CLIENTE.name(), response.getRol(), "El rol asignado debe ser forzosamente CLIENTE");
    }

    @Test
    @DisplayName("Debe fallar al registrar con correo duplicado")
    void testRegisterDuplicateEmail() {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .nombre("Admin Duplicado")
                .direccion("Ciudad")
                .telefono("12345678")
                .email("admin@delivery.com") // Ya existe por DataInitializer
                .password("otraClave")
                .build();

        assertThrows(com.delivery.auth.exception.EmailAlreadyExistsException.class, () -> {
            authService.register(registerRequest);
        });
    }

    @Test
    @DisplayName("Debe fallar al iniciar sesión con contraseña incorrecta")
    void testLoginBadCredentials() {
        AuthRequest loginRequest = AuthRequest.builder()
                .email("admin@delivery.com")
                .password("password_incorrecto")
                .build();

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> {
            authService.login(loginRequest);
        });
    }

    @Autowired
    private com.delivery.auth.security.JwtService jwtService;

    @Test
    @DisplayName("Debe rechazar token JWT expirado")
    void testTokenExpiradoRechazado() {
        // Generar un token con expiración en el pasado (-10 segundos)
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);

        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .subject("admin@delivery.com")
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 60000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> {
            jwtService.extractUsername(expiredToken);
        }, "Un token expirado debe ser rechazado arrojando ExpiredJwtException");
    }

    @Test
    @DisplayName("Debe rechazar token JWT con firma alterada o inválida")
    void testTokenFirmaInvalidaRechazado() {
        // Generar un token firmado con una clave diferente
        byte[] fakeKeyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode("1111111111111111111111111111111111111111111111111111111111111111");
        javax.crypto.SecretKey fakeKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(fakeKeyBytes);

        String fakeToken = io.jsonwebtoken.Jwts.builder()
                .subject("admin@delivery.com")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 60000))
                .signWith(fakeKey)
                .compact();

        assertThrows(io.jsonwebtoken.security.SignatureException.class, () -> {
            jwtService.extractUsername(fakeToken);
        }, "Un token firmado con clave desconocida debe arrojar SignatureException");
    }

    @Test
    @DisplayName("Debe rechazar token alterado o corrupto")
    void testTokenCorruptoRechazado() {
        AuthRequest loginRequest = AuthRequest.builder()
                .email("admin@delivery.com")
                .password("admin123")
                .build();
        AuthResponse response = authService.login(loginRequest);
        String validToken = response.getToken();

        // Alterar caracteres en el payload del token
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        assertThrows(io.jsonwebtoken.JwtException.class, () -> {
            jwtService.extractUsername(tamperedToken);
        }, "Un token alterado debe arrojar una excepción JwtException");
    }
}
