package com.delivery.auth.config;

import com.delivery.auth.model.entity.Usuario;
import com.delivery.auth.model.enums.Rol;
import com.delivery.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        crearOActualizarUsuario("Admin Sistema", "admin@delivery.com", "Ciudad de Guatemala", "55551111", "admin123", Rol.ADMIN);
        crearOActualizarUsuario("Repartidor Express", "repartidor@delivery.com", "Mixco, Guatemala", "55552222", "admin123", Rol.REPARTIDOR);
        crearOActualizarUsuario("Cliente Frecuente", "cliente@delivery.com", "Zona 10, Guatemala", "55553333", "admin123", Rol.CLIENTE);
    }

    private void crearOActualizarUsuario(String nombre, String email, String direccion, String telefono, String rawPassword, Rol rol) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) {
            usuario = Usuario.builder()
                    .nombre(nombre)
                    .direccion(direccion)
                    .telefono(telefono)
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .rol(rol)
                    .build();
            usuarioRepository.save(usuario);
            log.info("Usuario creado: {} con rol {}", email, rol);
        } else {
            usuario.setPassword(passwordEncoder.encode(rawPassword));
            usuario.setRol(rol);
            usuarioRepository.save(usuario);
            log.info("Usuario actualizado con clave cifrada: {}", email);
        }
    }
}
