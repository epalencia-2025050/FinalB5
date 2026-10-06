package com.delivery.pedido.config;

import com.delivery.pedido.security.JwtTokenFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenFilter jwtTokenFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // POST /api/v1/pedidos y GET /api/v1/pedidos/mis-pedidos solo para CLIENTE
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/mis-pedidos").hasRole("CLIENTE")

                        // GET /api/v1/pedidos/disponibles y PATCH estado para REPARTIDOR y ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/disponibles").hasAnyRole("REPARTIDOR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/estado").hasAnyRole("REPARTIDOR", "ADMIN")

                        // Cancelación (PATCH /cancelar o DELETE /{id}) para CLIENTE y ADMIN
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/cancelar").hasAnyRole("CLIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/pedidos/*").hasAnyRole("CLIENTE", "ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
