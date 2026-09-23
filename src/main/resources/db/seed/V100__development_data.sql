-- Development data: users to log in with, two practitioners with their reasons and weeks, and
-- two patients, so the application can be used right after starting it.
--
-- This lives in db/seed, a Flyway location of its own, and not in db/migration: the schema
-- belongs to every environment, this data only to development. A real deployment drops
-- classpath:db/seed from spring.flyway.locations and creates its users another way (D-34).
--
-- Passwords (BCrypt): recepcion / recepcion123, arias / arias123, benitez / benitez123.

insert into practitioner (id, full_name) values
    ('11111111-1111-1111-1111-111111111111', 'Dra. Laura Arias'),
    ('22222222-2222-2222-2222-222222222222', 'Dr. Martín Benítez');

insert into reason (id, practitioner_id, name, duration_minutes) values
    ('a1111111-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'consulta', 30),
    ('a1111111-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'control', 20),
    ('a1111111-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'ecografía', 45),
    ('b2222222-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'consulta', 20),
    ('b2222222-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'curación', 15),
    ('b2222222-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'certificado', 10);

-- Arias: Monday to Friday, 09:00-13:00 and 14:00-18:00. Benítez: Monday, Wednesday and Friday
-- afternoons.
insert into working_hours (practitioner_id, day_of_week, starts_at, ends_at) values
    ('11111111-1111-1111-1111-111111111111', 'MONDAY', '09:00', '13:00'),
    ('11111111-1111-1111-1111-111111111111', 'MONDAY', '14:00', '18:00'),
    ('11111111-1111-1111-1111-111111111111', 'TUESDAY', '09:00', '13:00'),
    ('11111111-1111-1111-1111-111111111111', 'TUESDAY', '14:00', '18:00'),
    ('11111111-1111-1111-1111-111111111111', 'WEDNESDAY', '09:00', '13:00'),
    ('11111111-1111-1111-1111-111111111111', 'WEDNESDAY', '14:00', '18:00'),
    ('11111111-1111-1111-1111-111111111111', 'THURSDAY', '09:00', '13:00'),
    ('11111111-1111-1111-1111-111111111111', 'THURSDAY', '14:00', '18:00'),
    ('11111111-1111-1111-1111-111111111111', 'FRIDAY', '09:00', '13:00'),
    ('11111111-1111-1111-1111-111111111111', 'FRIDAY', '14:00', '18:00'),
    ('22222222-2222-2222-2222-222222222222', 'MONDAY', '14:00', '20:00'),
    ('22222222-2222-2222-2222-222222222222', 'WEDNESDAY', '14:00', '20:00'),
    ('22222222-2222-2222-2222-222222222222', 'FRIDAY', '14:00', '20:00');

insert into patient (id, full_name, document_type, document_number) values
    ('c3333333-0000-0000-0000-000000000001', 'Ana Pérez', 'DNI', '30111222'),
    ('c3333333-0000-0000-0000-000000000002', 'Juan Gómez', null, null);

insert into app_user (id, username, password_hash, role, practitioner_id, active) values
    ('d4444444-0000-0000-0000-000000000001', 'recepcion',
     '$2a$10$YR9EAx9ejQUaQJDwsYQl5OXi5.GrD2V.8pCsMmW0/jflTXS0.3jKW', 'RECEPTION', null, true),
    ('d4444444-0000-0000-0000-000000000002', 'arias',
     '$2a$10$ASI7VdIeFdUGAcTsoXPAGOjobyWGk4.AW9JzZON4BV1gn.ycqBTCO', 'PRACTITIONER',
     '11111111-1111-1111-1111-111111111111', true),
    ('d4444444-0000-0000-0000-000000000003', 'benitez',
     '$2a$10$kj0ni/ZnJbGhJkO4m2Boy.9HDtCdXvyFY3QJUQQ5oI..sbz3y7CVm', 'PRACTITIONER',
     '22222222-2222-2222-2222-222222222222', true);
