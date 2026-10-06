package com.delivery.pedido;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String JWT_SECRET_TEST = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private String generateToken(String username, String role, Long userId) {
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET_TEST);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        Map<String, Object> claims = new HashMap<>();
        if (userId != null) {
            claims.put("userId", userId);
        }
        claims.put("role", role);
        claims.put("nombre", username);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private String generateExpiredToken(String username, String role, Long userId) {
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET_TEST);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        Map<String, Object> claims = new HashMap<>();
        if (userId != null) {
            claims.put("userId", userId);
        }
        claims.put("role", role);
        claims.put("nombre", username);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis() - 60000))
                .expiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Debe rechazar la creación de pedido sin autenticación (401 o 403)")
    void testCrearPedidoSinAutenticacionRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Esperado 401 o 403 sin token, obtenido: " + status);
                });
    }

    @Test
    @DisplayName("Debe rechazar con 403 cuando un REPARTIDOR intenta crear un pedido")
    void testRepartidorNoPuedeCrearPedido() throws Exception {
        String tokenRepartidor = generateToken("repartidor@delivery.com", "REPARTIDOR", 5L);

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenRepartidor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Debe rechazar con 403 cuando un CLIENTE intenta ver pedidos disponibles de repartidores")
    void testClienteNoPuedeVerPedidosDisponibles() throws Exception {
        String tokenCliente = generateToken("cliente@delivery.com", "CLIENTE", 2L);

        mockMvc.perform(get("/api/v1/pedidos/disponibles")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Debe rechazar JWT que no contiene claim userId y NUNCA asumir clienteId = 1 por defecto")
    void testJwtSinUserIdEsRechazadoYSinFallback() throws Exception {
        // Token firmado válidamente pero SIN claim userId
        String tokenSinUserId = generateToken("anonimo@delivery.com", "CLIENTE", null);

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenSinUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":1,\"cantidad\":1}]}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Un JWT sin userId debe ser rechazado con 401 o 403, jamás asumir clienteId=1. Obtenido: " + status);
                });
    }

    @Test
    @DisplayName("Debe rechazar consulta de mis-pedidos si el JWT no contiene userId")
    void testListarMisPedidosSinUserIdEsRechazado() throws Exception {
        String tokenSinUserId = generateToken("anonimo@delivery.com", "CLIENTE", null);

        mockMvc.perform(get("/api/v1/pedidos/mis-pedidos")
                        .header("Authorization", "Bearer " + tokenSinUserId))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Esperado 401 o 403 para mis-pedidos sin userId. Obtenido: " + status);
                });
    }

    @Test
    @DisplayName("Debe rechazar petición con token JWT expirado")
    void testTokenExpiradoRechazado() throws Exception {
        String expiredToken = generateExpiredToken("cliente@delivery.com", "CLIENTE", 2L);

        mockMvc.perform(get("/api/v1/pedidos/mis-pedidos")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Esperado 401 o 403 para token expirado. Obtenido: " + status);
                });
    }
}
