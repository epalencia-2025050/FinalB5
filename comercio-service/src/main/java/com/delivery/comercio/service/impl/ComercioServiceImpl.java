package com.delivery.comercio.service.impl;

import com.delivery.comercio.dto.request.ComercioRequest;
import com.delivery.comercio.dto.response.ComercioResponse;
import com.delivery.comercio.exception.ResourceNotFoundException;
import com.delivery.comercio.model.entity.Comercio;
import com.delivery.comercio.model.enums.CategoriaComercio;
import com.delivery.comercio.repository.ComercioRepository;
import com.delivery.comercio.service.IComercioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ComercioServiceImpl implements IComercioService {

    private final ComercioRepository comercioRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ComercioResponse> listarComercios(CategoriaComercio categoria) {
        List<Comercio> comercios;
        if (categoria != null) {
            comercios = comercioRepository.findByCategoriaAndAbiertoTrue(categoria);
        } else {
            comercios = comercioRepository.findByAbiertoTrue();
        }
        return comercios.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public ComercioResponse registrarComercio(ComercioRequest request) {
        Comercio comercio = Comercio.builder()
                .nombre(request.getNombre())
                .categoria(request.getCategoria())
                .direccion(request.getDireccion())
                .abierto(request.getAbierto() != null ? request.getAbierto() : true)
                .build();

        Comercio saved = comercioRepository.save(comercio);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ComercioResponse obtenerPorId(Long id) {
        Comercio comercio = comercioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado con ID: " + id));
        return mapToResponse(comercio);
    }

    private ComercioResponse mapToResponse(Comercio c) {
        return ComercioResponse.builder()
                .id(c.getId())
                .nombre(c.getNombre())
                .categoria(c.getCategoria())
                .direccion(c.getDireccion())
                .abierto(c.getAbierto())
                .build();
    }
}
