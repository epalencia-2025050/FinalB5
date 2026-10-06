package com.delivery.gateway.controller;

import com.delivery.gateway.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
public class GatewayProxyController {

    private final HttpClient httpClient;

    @Value("${gateway.services.auth:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${gateway.services.comercio:http://localhost:8082}")
    private String comercioServiceUrl;

    @Value("${gateway.services.pedido:http://localhost:8083}")
    private String pedidoServiceUrl;

    private static final Set<String> DISALLOWED_REQUEST_HEADERS = Set.of(
            "host", "content-length", "connection"
    );

    public GatewayProxyController() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @RequestMapping("/api/v1/**")
    public ResponseEntity<?> proxyRequest(HttpServletRequest request) {
        String uriPath = request.getRequestURI();
        String queryString = request.getQueryString();

        // 1. Determinar el microservicio destino según el prefijo de ruta
        String targetBaseUrl;
        if (uriPath.startsWith("/api/v1/auth")) {
            targetBaseUrl = authServiceUrl;
        } else if (uriPath.startsWith("/api/v1/comercios")) {
            targetBaseUrl = comercioServiceUrl;
        } else if (uriPath.startsWith("/api/v1/pedidos")) {
            targetBaseUrl = pedidoServiceUrl;
        } else {
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.NOT_FOUND.value())
                    .error("Not Found")
                    .message("Ruta no reconocida por el API Gateway: " + uriPath)
                    .path(uriPath)
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // Construir la URL completa de destino preservando query params (ej. ?categoria=RESTAURANTE)
        String targetUrl = targetBaseUrl + uriPath + (queryString != null ? "?" + queryString : "");

        try {
            // 2. Construir la petición HTTP a enviar al microservicio
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(20));

            // Copiar cabeceras entrantes (ej. Authorization: Bearer <jwt>)
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (!DISALLOWED_REQUEST_HEADERS.contains(headerName.toLowerCase())) {
                    Enumeration<String> values = request.getHeaders(headerName);
                    while (values.hasMoreElements()) {
                        reqBuilder.header(headerName, values.nextElement());
                    }
                }
            }

            // Configurar método y cuerpo
            byte[] bodyBytes = request.getInputStream().readAllBytes();
            String method = request.getMethod().toUpperCase();
            if ("GET".equals(method)) {
                reqBuilder.GET();
            } else if ("DELETE".equals(method)) {
                reqBuilder.DELETE();
            } else {
                reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            }

            // 3. Ejecutar la llamada hacia el microservicio
            HttpResponse<byte[]> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

            // 4. Copiar cabeceras de respuesta y devolver el resultado íntegro al cliente
            HttpHeaders responseHeaders = new HttpHeaders();
            response.headers().map().forEach((key, values) -> {
                if (!"content-length".equalsIgnoreCase(key) && !"transfer-encoding".equalsIgnoreCase(key)) {
                    responseHeaders.put(key, values);
                }
            });

            return ResponseEntity.status(response.statusCode())
                    .headers(responseHeaders)
                    .body(response.body());

        } catch (IOException | InterruptedException ex) {
            log.error("Error al enrutar petición a {}: {}", targetUrl, ex.getMessage());
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.BAD_GATEWAY.value())
                    .error("Bad Gateway")
                    .message("No se pudo establecer conexión con el microservicio en " + targetBaseUrl)
                    .path(uriPath)
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error);
        }
    }
}
