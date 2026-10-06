package com.delivery.auth.service.impl;

import com.delivery.auth.dto.request.AuthRequest;
import com.delivery.auth.dto.request.RegisterRequest;
import com.delivery.auth.dto.response.AuthResponse;
import com.delivery.auth.exception.EmailAlreadyExistsException;
import com.delivery.auth.model.entity.Usuario;
import com.delivery.auth.model.enums.Rol;
import com.delivery.auth.repository.UsuarioRepository;
import com.delivery.auth.security.JwtService;
import com.delivery.auth.service.IAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("El correo ya se encuentra registrado: " + request.getEmail());
        }

        // Seguridad: El registro público SIEMPRE asigna Rol.CLIENTE para prevenir escalamiento de privilegios
        Rol userRol = Rol.CLIENTE;

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .direccion(request.getDireccion())
                .telefono(request.getTelefono())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(userRol)
                .build();

        usuarioRepository.save(usuario);

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", usuario.getId());
        extraClaims.put("role", usuario.getRol().name());
        extraClaims.put("nombre", usuario.getNombre());

        String jwtToken = jwtService.generateToken(usuario, extraClaims);

        return AuthResponse.builder()
                .token(jwtToken)
                .type("Bearer")
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .build();
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", usuario.getId());
        extraClaims.put("role", usuario.getRol().name());
        extraClaims.put("nombre", usuario.getNombre());

        String jwtToken = jwtService.generateToken(usuario, extraClaims);

        return AuthResponse.builder()
                .token(jwtToken)
                .type("Bearer")
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .build();
    }
}

