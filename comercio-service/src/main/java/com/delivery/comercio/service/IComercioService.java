package com.delivery.comercio.service;

import com.delivery.comercio.dto.request.ComercioRequest;
import com.delivery.comercio.dto.response.ComercioResponse;
import com.delivery.comercio.model.enums.CategoriaComercio;

import java.util.List;

public interface IComercioService {
    List<ComercioResponse> listarComercios(CategoriaComercio categoria);
    ComercioResponse registrarComercio(ComercioRequest request);
    ComercioResponse obtenerPorId(Long id);
}
