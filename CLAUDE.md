# schedule — Clinic appointment schedule

A Spring Boot application for the internal appointment schedule of a small medical
practice. It is built **in stages**: implement only what the current stage needs.

## Reference documents

Read these before proposing design or code:

- `docs/context.md` — what the application does, who uses it, the business rules and the
  planned evolution.
- `docs/decisions.md` — technical decisions and their reasoning (D-01 … D-14).
- `docs/build.md` — the staged plan and the current progress.

## Stack

Maven · Java 21 · Spring Boot 3.5 · Spring Data JPA · Flyway · H2 (PostgreSQL mode).

`JAVA_HOME` must point at JDK 21: this machine defaults to JDK 25. See `README.md`.

## Working rules

- **No scaffolding (D-14).** Nothing is added before the stage that uses it — not code, not
  dependencies, not configuration, not annotations. "It will be needed later" is not a
  reason; it is what gets it removed. Stage 1 was already trimmed once for this.
- **Always separate v1 from future work.** If something belongs to a later stage, say so
  and do not implement it.
- **Language (D-13):** everything in the repository is written in **English** — code,
  comments, SQL, API messages, tests, documents and commit messages. The only exception is
  the on-screen text the staff reads, which stays in **Spanish**. Conversation with the
  user is in Spanish.
- **Undecided technical questions:** propose the simplest option and explain it before
  treating it as agreed. Once accepted, add it to `docs/decisions.md`.
- **Module boundaries:** `patient/`, `practitioner/`, `appointment/`. A module only uses
  another module's `api/` package (interface plus DTOs). Never import entities or
  repositories from another module. Each module may organize its internals as it sees fit.
- **The booking domain works with a start time and a duration** and knows nothing about
  what the appointment reason means. The duration is resolved by the application layer,
  which asks `practitioner`.
- **Entity and domain services are separate, with a single way in (D-17).** The entity
  guards its own invariants and has no unvalidated creation path. Rules that need context
  the entity cannot have live in a domain service, which is the only way in: the entity's
  mutating operations stay package-private. A domain service receives the context as a
  collection and never reads it — fetching data and the transaction are the use case's job,
  so the domain has no ports. Aggregates are immutable: operations return a new instance, so
  a rejected rule leaves nothing half-changed.
- **Naming (D-18).** Either the folder says what a file is, or the name must. Where kinds
  share a folder, use the suffix of the kind: `Exception`, `Service`, `Policy`,
  `Reader`/`Writer`/`Repository` for storage depending on whether it reads, writes or both,
  `Port` for a dependency that is not storage, `UseCase`, `Adapter`,
  `Row`, `Controller`, `Request`/`Response`. Entities and value objects take no suffix —
  they are the vocabulary of the business. A `Command` used by a single use case is a record
  nested inside it. The full table is in `docs/decisions.md`.
- **Nothing engine-specific.** Standard JPA and Flyway migrations in portable SQL, so
  moving to PostgreSQL only means changing the datasource. Watch out for reserved words:
  `user` is reserved, which is why the user table will be `app_user`.
- **Time (from stage 4 on):** the fixed time zone is `America/Argentina/Buenos_Aires`. Use
  the injected `Clock` bean, never `LocalDateTime.now()`, so the deadlines stay testable.
- **Tests:** unit tests on the domain rules and `@SpringBootTest` on the key flows. Do not
  add per-layer tests just to raise coverage.
- **No authentication until stage 8.** Endpoints that need to know the practitioner take
  the `practitionerId` as a parameter. Do not introduce a stand-in for the current user.
- **Commits:** use the `git-commit` skill (in `.claude/skills/git-commit/`), Conventional
  Commits format. Commit only when asked.
- **When a stage is finished:** update `docs/build.md`.

## Domain glossary

patient · practitioner · appointment (`BOOKED`/`CANCELLED`) · reason · duration ·
weekly schedule · break · time off · check-in · reschedule · reception
