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
        if (!usuarioRepository.existsByEmail("admin@delivery.com")) {
            Usuario admin = Usuario.builder()
                    .nombre("Admin Sistema")
                    .direccion("Ciudad de Guatemala")
                    .telefono("55551111")
                    .email("admin@delivery.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol(Rol.ADMIN)
                    .build();
            usuarioRepository.save(admin);
            log.info("Usuario ADMIN inicial creado: admin@delivery.com");
        }

        if (!usuarioRepository.existsByEmail("repartidor@delivery.com")) {
            Usuario repartidor = Usuario.builder()
                    .nombre("Repartidor Express")
                    .direccion("Mixco, Guatemala")
                    .telefono("55552222")
                    .email("repartidor@delivery.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol(Rol.REPARTIDOR)
                    .build();
            usuarioRepository.save(repartidor);
            log.info("Usuario REPARTIDOR inicial creado: repartidor@delivery.com");
        }

        if (!usuarioRepository.existsByEmail("cliente@delivery.com")) {
            Usuario cliente = Usuario.builder()
                    .nombre("Cliente Frecuente")
                    .direccion("Zona 10, Guatemala")
                    .telefono("55553333")
                    .email("cliente@delivery.com")
                    .password(passwordEncoder.encode("admin123"))
                    .rol(Rol.CLIENTE)
                    .build();
            usuarioRepository.save(cliente);
            log.info("Usuario CLIENTE inicial creado: cliente@delivery.com");
        }
    }
}
