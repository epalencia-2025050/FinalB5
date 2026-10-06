package com.delivery.comercio.repository;

import com.delivery.comercio.model.entity.Comercio;
import com.delivery.comercio.model.enums.CategoriaComercio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComercioRepository extends JpaRepository<Comercio, Long> {
    List<Comercio> findByAbiertoTrue();
    List<Comercio> findByCategoriaAndAbiertoTrue(CategoriaComercio categoria);
}
