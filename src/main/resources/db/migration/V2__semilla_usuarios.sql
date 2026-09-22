-- Usuarios de desarrollo. Claves BCrypt de: recepcion123 y profesional123.
-- Estas credenciales existen para poder usar la aplicacion sin pantalla de administracion
-- de usuarios (D-04). Antes de cualquier despliegue real hay que reemplazarlas.
--
-- profesional_id 1 y 2 apuntan a los profesionales que crea la etapa 3; recien ahi se
-- agrega la clave foranea.
insert into usuario (usuario, clave_hash, rol, profesional_id, activo) values
    ('recepcion', '$2a$10$8vco8srNl56CaW6PmN417.JsTk6SYVresGtpyRlMoqWCqnxYH/Tjy', 'RECEPCION',   null, true),
    ('arias',     '$2a$10$kujAMzcuF0wyUmV6.UvTJ.riTM7QUBIVsXNSb1K0LmKlSPuYX1Se6', 'PROFESIONAL', 1,    true),
    ('benitez',   '$2a$10$kujAMzcuF0wyUmV6.UvTJ.riTM7QUBIVsXNSb1K0LmKlSPuYX1Se6', 'PROFESIONAL', 2,    true);
