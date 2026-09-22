# java-app — Agenda de consultorio

Aplicación Spring Boot para la agenda interna de un consultorio médico pequeño.
Se construye **por etapas**: implementá solo lo que la etapa actual necesita, sin
adelantar funcionalidad futura ni sobreingeniería.

## Documentos de referencia

Leelos antes de proponer diseño o código:

- `docs/contexto.md` — qué hace la aplicación, quiénes la usan, reglas de negocio y
  evolución prevista.
- `docs/decisiones.md` — decisiones técnicas con su motivo (D-01 … D-12).
- `docs/build.md` — plan por etapas y estado de avance.

## Stack

Maven · Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA · Flyway ·
H2 (modo PostgreSQL) · Thymeleaf.

## Reglas de trabajo

- **Distinguí siempre v1 de futuro.** Si algo pertenece a una etapa posterior, decilo
  y no lo implementes.
- **Decisiones no definidas:** proponé la opción más simple y explicala antes de
  tratarla como acordada. Si se acepta, agregala a `docs/decisiones.md`.
- **Límites entre módulos:** `paciente/`, `profesional/`, `turno/`. Un módulo solo usa
  el paquete `api/` de otro (interfaz + DTOs). Nunca importes entidades ni repositorios
  de otro módulo. Cada módulo puede organizarse internamente como le convenga.
- **El dominio de reservas trabaja con inicio y duración** y no conoce el significado de
  la razón del turno. La duración la resuelve la capa de aplicación consultando a
  `profesional`.
- **Las vistas Thymeleaf son delgadas y descartables:** llaman a los application services,
  nunca a los controladores REST, y no contienen lógica. La API REST es el contrato
  principal porque más adelante habrá dos SPA.
- **Nada específico del motor de base.** JPA estándar y migraciones Flyway en SQL portable,
  para poder pasar a PostgreSQL cambiando solo el datasource.
- **Tiempo:** zona horaria fija `America/Argentina/Buenos_Aires`. Usá el bean `Clock`
  inyectado, nunca `LocalDateTime.now()` directo, para que los plazos sean testeables.
- **Tests:** unitarios sobre las reglas del dominio y `@SpringBootTest` sobre los flujos
  clave. No agregues tests por capa solo para subir cobertura.
- **Commits:** usá la skill `git-commit` (en `.claude/skills/git-commit/`), formato
  Conventional Commits. Commiteá solo cuando se pida.
- **Al terminar una etapa:** actualizá `docs/build.md`.
