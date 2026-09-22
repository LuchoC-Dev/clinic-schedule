# Progreso de construcción

Estado del plan por etapas. Cada etapa es entregable y verificable por sí sola.
Se actualiza al completar cada etapa.

**Leyenda:** `[ ]` pendiente · `[~]` en curso · `[x]` hecho

---

## v1 — Agenda interna

### Etapa 1 — Esqueleto y seguridad `[x]`
- [x] Proyecto Maven con Spring Boot 3.5.16 y Java 21 (web, data-jpa, security, validation, thymeleaf, flyway, h2) más Maven Wrapper
- [x] Estructura de paquetes por módulo y `README.md`
- [x] Configuración: H2 en modo PostgreSQL, zona horaria fija, `Clock` como bean
- [x] Migraciones Flyway: `V1` esquema de usuario, `V2` semilla de usuarios
- [x] Spring Security con roles `RECEPCION` y `PROFESIONAL`, login y `SesionActual` como api del módulo
- [x] Contrato de errores homogéneo: 400 de validación, 401, 403, 404 y 409

**Verificado con:** 8 tests en verde (`mvnw test`) y la aplicación levantada —
`/login` responde 200, `/api/sesion` responde 401 sin credenciales y devuelve el rol y
el `profesionalId` correctos para `recepcion` y para `arias`.

**Notas de la etapa:**
- `spring-boot-starter-parent` 3.5.16 es la última 3.5.x. La máquina tiene JDK 21 y 25;
  el `PATH` apunta al 25, así que hay que fijar `JAVA_HOME` al 21 (ver `README.md`).
- `flyway-database-h2` no existe como artefacto: H2 lo soporta `flyway-core` directamente.
  Al pasar a PostgreSQL sí hay que agregar `flyway-database-postgresql`.
- `usuario.profesional_id` queda sin clave foránea hasta la etapa 3, cuando exista la
  tabla de profesionales. La semilla usa los ids 1 y 2, que esa etapa debe crear.
- `ddl-auto: validate` convierte el arranque del contexto en una verificación de que el
  esquema de Flyway y el mapeo JPA coinciden.

### Etapa 2 — Módulo paciente
- [ ] Entidad y migración: id interno, nombre completo, DNI opcional con único parcial
- [ ] Alta de paciente y búsqueda por nombre o DNI
- [ ] Endpoints REST y `api/` del módulo para que `turno` resuelva un paciente
- [ ] Tests: DNI duplicado rechazado, varios pacientes sin DNI, nombres repetidos permitidos

### Etapa 3 — Módulo profesional
- [ ] Entidades y migraciones: profesional, horario semanal, pausas
- [ ] Razones con duración fija por profesional
- [ ] Configuración por recepción (endpoints) y consulta propia del profesional
- [ ] `api/` del módulo: duración de una razón y comprobación de disponibilidad
- [ ] Tests: duración por razón, horario fuera de disponibilidad, hora dentro de una pausa

### Etapa 4 — Módulo turno: reservar, reprogramar, cancelar
- [ ] Entidad y migración del turno con estado `RESERVADO`/`CANCELADO`
- [ ] Dominio de reserva con inicio y duración, sin conocer la razón
- [ ] Reglas: sin solapamiento por profesional, sin simultáneos por paciente, dentro de disponibilidad
- [ ] Reservar, reprogramar y cancelar; los cancelados se conservan
- [ ] Filtrado por rol en la consulta de agenda
- [ ] Tests: solapamientos en los bordes, mismo paciente en dos profesionales a la misma hora, turno del mismo día

### Etapa 5 — Llegada y cancelación automática
- [ ] Confirmar llegada y suspender la cancelación automática
- [ ] Tarea `@Scheduled` cada minuto: 30 minutos sin llegada, 24 horas si está suspendida
- [ ] Tests con `Clock` fijo para los tres casos y para el turno ya atendido

### Etapa 6 — Ausencias del profesional
- [ ] Registrar ausencia por día o por intervalo horario
- [ ] Cancelación en cascada de los turnos afectados **sin** llegada confirmada
- [ ] Informe de los turnos con llegada confirmada que requieren intervención manual
- [ ] Tests: ausencia parcial, ausencia de día completo, turno con llegada confirmada intacto

### Etapa 7 — Vistas Thymeleaf mínimas
- [ ] Agenda del día por profesional
- [ ] Alta y reprogramación de turno con sugerencia de intervalos en la interfaz
- [ ] Confirmar llegada desde la agenda
- [ ] Vista del profesional restringida a sus propios turnos

---

## Postergado explícitamente

No son omisiones: están decididos y esperando su momento.

- **PostgreSQL** reemplazando a H2, con `docker-compose` (ver D-03).
- **ArchUnit** verificando los límites entre módulos (ver D-05).
- Administración de usuarios por interfaz (ver D-04).

## Fuera del alcance de la v1

Ver `docs/contexto.md`, sección «Evolución prevista»: ficha de paciente, cola de llegada,
historia clínica, asistencia con más estados, avisos, datos adicionales del paciente y
reserva web.
