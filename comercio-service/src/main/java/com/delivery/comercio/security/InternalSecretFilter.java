package com.delivery.comercio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private final String internalApiSecret;

    public InternalSecretFilter(@Value("${internal.api.secret:${INTERNAL_API_SECRET:delivery-internal-secret-token-key-2026}}") String internalApiSecret) {
        this.internalApiSecret = internalApiSecret;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/internal/") || path.contains("/internal/")) {
            String headerSecret = request.getHeader("X-Internal-Secret");

            if (headerSecret == null || !headerSecret.equals(internalApiSecret)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Acceso denegado: credencial interna no valida o ausente\",\"path\":\"" + path + "\"}");
                return;
            }

            // Credencial interna v?lida: asignar autenticaci?n con rol INTERNAL_SERVICE
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "internal-microservice",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
