package com.delivery.comercio;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ComercioSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${internal.api.secret}")
    private String internalSecret;

    private static final String JWT_SECRET_TEST = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private String generateToken(String username, String role, Long userId) {
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET_TEST);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .claim("nombre", username)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private String generateExpiredToken(String username, String role, Long userId) {
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET_TEST);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .claim("nombre", username)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis() - 60000))
                .expiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();
    }

    private String generateTamperedKeyToken(String username, String role, Long userId) {
        byte[] fakeKeyBytes = Decoders.BASE64.decode("1111111111111111111111111111111111111111111111111111111111111111");
        SecretKey key = Keys.hmacShaKeyFor(fakeKeyBytes);
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .claim("nombre", username)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Debe rechazar consulta de comercios sin token JWT (401 o 403)")
    void testConsultaComerciosSinTokenRechazada() throws Exception {
        mockMvc.perform(get("/api/v1/comercios"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                            "Esperado 401 o 403, obtenido: " + status);
                });
    }

    @Test
    @DisplayName("Debe permitir consulta de comercios con token de CLIENTE autenticado (200 OK)")
    void testConsultaComerciosConTokenAutenticado() throws Exception {
        String token = generateToken("cliente@delivery.com", "CLIENTE", 2L);
        mockMvc.perform(get("/api/v1/comercios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Debe denegar creación de comercio a un usuario con rol CLIENTE (403 Forbidden)")
    void testCrearComercioConRolClienteDenegado() throws Exception {
        String token = generateToken("cliente@delivery.com", "CLIENTE", 2L);
        mockMvc.perform(post("/api/v1/comercios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Comercio No Autorizado\",\"categoria\":\"RESTAURANTE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Debe rechazar acceso a endpoint interno sin header X-Internal-Secret (403 Forbidden)")
    void testEndpointInternoSinSecretRechazado() throws Exception {
        mockMvc.perform(post("/internal/productos/1/deduct-stock")
                        .param("cantidad", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Debe rechazar acceso a endpoint interno con header X-Internal-Secret inválido (403 Forbidden)")
    void testEndpointInternoConSecretInvalidoRechazado() throws Exception {
        mockMvc.perform(post("/internal/productos/1/deduct-stock")
                        .header("X-Internal-Secret", "secret_invalido_erroneo")
                        .param("cantidad", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Debe autorizar endpoint interno cuando se envía header X-Internal-Secret válido")
    void testEndpointInternoConSecretValidoAutorizado() throws Exception {
        mockMvc.perform(post("/internal/productos/1/restore-stock")
                        .header("X-Internal-Secret", internalSecret)
                        .param("cantidad", "0"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Debe rechazar petición con token JWT expirado (401 o 403)")
    void testTokenExpiradoRechazado() throws Exception {
        String expiredToken = generateExpiredToken("cliente@delivery.com", "CLIENTE", 2L);
        mockMvc.perform(get("/api/v1/comercios")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                            "Esperado 401 o 403 para token expirado, obtenido: " + status);
                });
    }

    @Test
    @DisplayName("Debe rechazar petición con token JWT de firma alterada (401 o 403)")
    void testTokenFirmaInvalidaRechazado() throws Exception {
        String invalidToken = generateTamperedKeyToken("cliente@delivery.com", "CLIENTE", 2L);
        mockMvc.perform(get("/api/v1/comercios")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                            "Esperado 401 o 403 para token con firma falsa, obtenido: " + status);
                });
    }
}
