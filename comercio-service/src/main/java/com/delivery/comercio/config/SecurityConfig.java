package com.delivery.comercio.config;

import com.delivery.comercio.security.JwtTokenFilter;
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
    private final com.delivery.comercio.security.InternalSecretFilter internalSecretFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints internos solo accesibles con credencial interna verificada
                        .requestMatchers("/internal/**", "/api/v1/comercios/internal/**").hasRole("INTERNAL_SERVICE")
                        // POST /api/v1/comercios y POST /api/v1/comercios/{id}/productos solo para ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/v1/comercios/**").hasRole("ADMIN")
                        // GET /api/v1/comercios y GET /api/v1/comercios/{id}/productos REQUIERE AUTENTICACION segun rubrica
                        .requestMatchers(HttpMethod.GET, "/api/v1/comercios/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
