# Decisiones técnicas

Registro de decisiones con su motivo. Cada entrada tiene fecha, estado y alternativas
descartadas. Si una decisión se revierte, no se borra: se marca `Reemplazada por`.

---

## D-01 — Maven, Java 21, Spring Boot 3.5
**Fecha:** 2026-09-22 · **Estado:** aceptada

Stack más convencional en entornos Java corporativos, sobre un LTS ampliamente adoptado.

*Descartado:* Gradle (menos habitual en el mercado al que apunta el proyecto);
Java 25 (LTS más reciente, adopción todavía baja).

---

## D-02 — API REST como contrato principal, vistas Thymeleaf mínimas y descartables
**Fecha:** 2026-09-22 · **Estado:** aceptada

La interfaz futura serán dos SPA separadas, así que el contrato estable es la API REST.
Las vistas Thymeleaf de la v1 existen solo para ver la aplicación funcionando: agenda
del día, alta de turno, confirmar llegada.

**Condición de diseño que hace esto separable:** las vistas son delgadas y llaman a los
*application services* del módulo. Nunca consumen los controladores REST ni contienen
lógica propia. Cuando lleguen las SPA se borran `templates/` y los `@Controller` de
vista, y no se toca nada más.

*Descartado:* solo REST (no se ve la app funcionando); SPA propia en la v1 (mucho
trabajo fuera del foco).

---

## D-03 — H2 en modo PostgreSQL, sin acoplar al motor
**Fecha:** 2026-09-22 · **Estado:** aceptada

Arrancamos con H2 para no pedir infraestructura, pero sin atarnos a él:

- JPA/Hibernate estándar, sin funciones específicas del motor.
- H2 en modo de compatibilidad PostgreSQL.
- Migraciones **Flyway** en SQL portable desde el primer commit.

Pasar a PostgreSQL debe ser cambiar el datasource y la dependencia, nada más.

*Descartado:* `ddl-auto=update` (mala práctica visible en un repositorio de muestra);
PostgreSQL desde el inicio (postergado, no rechazado — ver `docs/build.md`).

---

## D-04 — Spring Security con roles RECEPCION y PROFESIONAL
**Fecha:** 2026-09-22 · **Estado:** aceptada

«Cada profesional ve solo sus turnos» es una regla de autorización real, no cosmética,
así que la v1 la implementa de verdad.

- Usuarios en base, cargados por migración (semilla). Sin pantalla de administración
  de usuarios en la v1.
- Usuario y profesional son entidades distintas: el usuario con rol `PROFESIONAL`
  referencia un `profesionalId`.
- El filtrado por rol vive en el servicio, no en el controlador: recepción puede
  consultar cualquier `profesionalId`; al profesional se le impone el suyo y se ignora
  el parámetro recibido.

*Descartado:* usuario simulado por header o parámetro (deja el requisito central sin
implementar).

---

## D-05 — Módulos por feature, capas simples, límites por convención
**Fecha:** 2026-09-22 · **Estado:** aceptada

Estructura `paciente/`, `profesional/`, `turno/`. Dentro de cada módulo, capas simples
(web, aplicación, dominio, persistencia). **Cada módulo puede tener una organización
interna distinta** según lo que necesite.

Regla de acoplamiento: un módulo solo se comunica con otro a través del paquete `api/`
de ese otro (interfaz + DTOs propios). El resto es package-private. Nunca se importan
entidades ni repositorios de otro módulo.

El límite se sostiene **por convención**, sin ArchUnit ni Spring Modulith: menos piezas
para tres módulos. El riesgo aceptado es que la erosión del límite no salte
automáticamente; se cuida en revisión.

*Descartado:* Spring Modulith (dependencia y conceptos extra); ArchUnit (postergado);
capas clásicas a nivel raíz (se desordenan al llegar la ficha de paciente y la cola).

---

## D-06 — Cancelación automática con `@Scheduled`, estado persistido
**Fecha:** 2026-09-22 · **Estado:** aceptada

Una tarea programada cada minuto barre los turnos vencidos y los cancela, dejando el
estado real en la base. Los dos plazos se distinguen con dos campos del turno:
`llegadaConfirmadaEn` y `cancelacionAutomaticaSuspendida`.

- Sin llegada y sin suspensión: se cancela a los 30 minutos del inicio.
- Sin llegada y con suspensión: se cancela a las 24 horas del inicio.
- Con llegada confirmada: no se cancela nunca automáticamente.

*Descartado:* cálculo perezoso al leer (el estado no queda persistido y complica el
historial que mostrará la ficha de paciente); scheduler con evento de dominio
(indirección sin uso hoy; se reevalúa cuando existan avisos o asistencia).

---

## D-07 — Turno como único agregado, con estado y marcas de llegada
**Fecha:** 2026-09-22 · **Estado:** aceptada

Un solo agregado `Turno` con estado `RESERVADO`/`CANCELADO` más los dos campos de D-06.
Sin tabla aparte para llegadas ni para suspensiones: la v1 no necesita más estados y la
funcionalidad de asistencia los reemplazará.

---

## D-08 — `turno` obtiene duración y disponibilidad desde el `api/` de `profesional`
**Fecha:** 2026-09-22 · **Estado:** aceptada

La capa de aplicación de `turno` consulta al módulo `profesional` dos cosas:

1. La duración fija de la razón para ese profesional.
2. Si el horario solicitado cae dentro de su disponibilidad (horario semanal, pausas,
   ausencias).

El dominio de reservas recibe **inicio y duración** ya resueltos y no conoce el
significado de la razón. `turno` no ve entidades de `profesional`, solo sus DTOs.

---

## D-09 — Reglas de integridad también en la base
**Fecha:** 2026-09-22 · **Estado:** aceptada

Además de la validación en el servicio:

- Índice único parcial para el DNI, que permite múltiples pacientes sin DNI.
- Verificación de solapamiento dentro de la transacción de reserva.

Con 3 o 4 profesionales no se justifica un esquema elaborado de locking.

---

## D-10 — Zona horaria fija de la aplicación
**Fecha:** 2026-09-22 · **Estado:** aceptada

`America/Argentina/Buenos_Aires` fija, `LocalDateTime` en el dominio. Sin soporte
multi-zona. El reloj se inyecta como `Clock` para poder testear los plazos de
cancelación.

---

## D-11 — Tests: dominio unitario más flujos clave de integración
**Fecha:** 2026-09-22 · **Estado:** aceptada

- Unitarios sobre las reglas: solapamiento, resolución de duración, plazos de cancelación.
- `@SpringBootTest` sobre los flujos clave: reservar, reprogramar, cancelar, confirmar
  llegada, registrar ausencia.

*Descartado:* cobertura completa por capa (mucho código para mantener mientras el diseño
se mueve); solo dominio (nadie verifica el cableado Spring ni las restricciones de base).

---

## D-12 — Commits convencionales mediante la skill `git-commit`
**Fecha:** 2026-09-22 · **Estado:** aceptada

La skill está copiada en `.claude/skills/git-commit/` para que el repositorio lleve su
propia convención de commits.
