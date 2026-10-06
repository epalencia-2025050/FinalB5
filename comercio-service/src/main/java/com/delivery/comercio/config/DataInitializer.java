package com.delivery.comercio.config;

import com.delivery.comercio.model.entity.Comercio;
import com.delivery.comercio.model.entity.Producto;
import com.delivery.comercio.model.enums.CategoriaComercio;
import com.delivery.comercio.repository.ComercioRepository;
import com.delivery.comercio.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ComercioRepository comercioRepository;
    private final ProductoRepository productoRepository;

    @Override
    public void run(String... args) {
        if (comercioRepository.count() == 0) {
            Comercio burgerKing = Comercio.builder()
                    .nombre("Burger King Central")
                    .categoria(CategoriaComercio.RESTAURANTE)
                    .direccion("Av. Las Americas 12-45, Zona 14")
                    .abierto(true)
                    .build();
            comercioRepository.save(burgerKing);

            Comercio laTorre = Comercio.builder()
                    .nombre("Supermercado La Torre")
                    .categoria(CategoriaComercio.SUPERMERCADO)
                    .direccion("Calzada Roosevelt 5-10, Zona 11")
                    .abierto(true)
                    .build();
            comercioRepository.save(laTorre);

            Producto p1 = Producto.builder()
                    .comercio(burgerKing)
                    .nombre("Whopper Doble Combo")
                    .precio(new BigDecimal("65.00"))
                    .stock(50)
                    .disponible(true)
                    .build();
            productoRepository.save(p1);

            Producto p2 = Producto.builder()
                    .comercio(burgerKing)
                    .nombre("Papas Fritas Medianas")
                    .precio(new BigDecimal("20.00"))
                    .stock(100)
                    .disponible(true)
                    .build();
            productoRepository.save(p2);

            Producto p3 = Producto.builder()
                    .comercio(laTorre)
                    .nombre("Leche Entera 1L")
                    .precio(new BigDecimal("16.50"))
                    .stock(80)
                    .disponible(true)
                    .build();
            productoRepository.save(p3);

            log.info("Datos iniciales de comercios y productos creados exitosamente");
        }
    }
}
