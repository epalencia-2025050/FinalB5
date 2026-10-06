-- BCrypt de 'admin123': $2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a
-- BCrypt de 'repartidor123': $2a$10$QO2mR5eD8N.e3yV2c6k4lONW4wK9fI5c/o3Yl90yC0K9X.h5xZc2i
-- BCrypt de 'cliente123': $2a$10$2l9qJzN8F6tK1yD4w2h3luu2h4j5k6l7m8n9o0p1q2r3s4t5u6v7w

INSERT INTO usuarios (nombre, direccion, telefono, email, password, rol)
VALUES ('Admin Sistema', 'Ciudad de Guatemala', '55551111', 'admin@delivery.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

INSERT INTO usuarios (nombre, direccion, telefono, email, password, rol)
VALUES ('Repartidor Fast', 'Mixco, Guatemala', '55552222', 'repartidor@delivery.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'REPARTIDOR')
ON CONFLICT (email) DO NOTHING;

INSERT INTO usuarios (nombre, direccion, telefono, email, password, rol)
VALUES ('Cliente Frecuente', 'Zona 10, Guatemala', '55553333', 'cliente@delivery.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a', 'CLIENTE')
ON CONFLICT (email) DO NOTHING;

