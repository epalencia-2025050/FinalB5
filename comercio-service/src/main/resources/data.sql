-- Inserción de al menos 1 comercio inicial
INSERT INTO comercios (id, nombre, categoria, direccion, abierto)
VALUES (1, 'Burger King Central', 'RESTAURANTE', 'Av. Las Américas 12-45, Zona 14', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO comercios (id, nombre, categoria, direccion, abierto)
VALUES (2, 'Supermercado La Torre', 'SUPERMERCADO', 'Calzada Roosevelt 5-10, Zona 11', true)
ON CONFLICT (id) DO NOTHING;

-- Inserción de productos asociados con stock inicial para pruebas
INSERT INTO productos (id, comercio_id, nombre, precio, stock, disponible)
VALUES (1, 1, 'Whopper Doble Combo', 65.00, 50, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO productos (id, comercio_id, nombre, precio, stock, disponible)
VALUES (2, 1, 'Papas Fritas Medianas', 20.00, 100, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO productos (id, comercio_id, nombre, precio, stock, disponible)
VALUES (3, 2, 'Leche Entera 1L', 16.50, 80, true)
ON CONFLICT (id) DO NOTHING;

-- Ajustar las secuencias de IDs para que nuevos inserts continúen sin error
SELECT setval(pg_get_serial_sequence('comercios', 'id'), coalesce(max(id), 1)) FROM comercios;
SELECT setval(pg_get_serial_sequence('productos', 'id'), coalesce(max(id), 1)) FROM productos;
