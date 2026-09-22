# Build progress

Status of the staged plan. Every stage is deliverable and verifiable on its own, and this
file is updated when a stage is finished.

Nothing is added before the stage that uses it (D-14) — that applies to dependencies and
configuration as much as to code.

**Legend:** `[ ]` pending · `[~]` in progress · `[x]` done

---

## v1 — Internal appointment schedule

### Stage 1 — A running application `[x]`
- [x] Maven project on Spring Boot 3.5.16 and Java 21, plus the Maven Wrapper
- [x] Dependencies limited to what runs today: web, data-jpa, flyway-core, h2, test
- [x] H2 in PostgreSQL compatibility mode, with Flyway in charge of the schema and `ddl-auto: validate`
- [x] `README.md` and the reference documents in `docs/`

**Verified with:** `mvnw clean test` passing, and the application starting and serving on
port 8080.

**Stage notes:**
- `spring-boot-starter-parent` 3.5.16 is the latest 3.5.x. This machine has both JDK 21 and
  JDK 25 and the `PATH` points at 25, so `JAVA_HOME` must be set to 21 (see `README.md`).
- `flyway-database-h2` does not exist as an artifact: `flyway-core` supports H2 directly.
  Moving to PostgreSQL does require `flyway-database-postgresql`.
- There are no endpoints yet, so any URL answers 404. The first one arrives in stage 2.
- Flyway logs `No migrations found` on startup. That is expected until stage 2 adds `V1`.
- This stage first shipped with security, users, login, an error contract, a `Clock` bean,
  `@EnableScheduling` and the thymeleaf and validation dependencies. All of it was removed
  as unused scaffolding (D-14), and each piece now belongs to the stage that needs it.

### In progress — `appointment` domain `[~]`

The order of the stages below is **under review**: `appointment` is the only module with
rules of its own, so its domain is being built first to check that the rules close before
committing to a plan. Done so far, in
`src/main/java/com/clinic/schedule/appointment/domain/`:

- [x] `TimeSlot` — start plus duration, with the exclusive-end overlap rule
- [x] `Appointment` — the aggregate: book, cancel, reschedule
- [x] `AppointmentId` — UUID assigned on creation (D-19), so a retried request cannot book twice
- [x] `PatientId` / `PractitionerId` — id wrappers, no cross-module relations
- [x] `BookingPolicy` — no practitioner overlap, no patient overlap (package-private)
- [x] `BookingService` — the only way in: creates and checks together (D-17)
- [x] `BookingRuleViolation` and the two named subclasses
- [x] 34 unit tests, no Spring, running in under half a second

Not here yet, by design: ports (D-14 — they arrive with the use case), persistence, the
REST endpoint, and the availability check (weekly schedule, breaks, time off), which
belongs to `practitioner`.

### Stage 2 — Patient module
- [ ] Add the `validation` dependency and the uniform error contract, with the first endpoint
- [ ] Entity and migration `V1`: internal id, full name, optional national ID with a partial unique index
- [ ] Register a patient, and search by name or national ID
- [ ] REST endpoints, and the module `api/` so `appointment` can resolve a patient
- [ ] Tests: duplicate national ID rejected, several patients without one, repeated names allowed

### Stage 3 — Practitioner module
- [ ] Entities and migrations: practitioner, weekly schedule, breaks
- [ ] Reasons with a fixed duration per practitioner
- [ ] Configuration endpoints for reception
- [ ] Module `api/`: duration of a reason, and an availability check
- [ ] Tests: duration per reason, a time outside availability, a time inside a break

### Stage 4 — Appointment module: book, reschedule, cancel
- [ ] Add the `Clock` bean and the fixed time zone, the first code that reads the time
- [ ] Entity and migration with the `BOOKED`/`CANCELLED` state
- [ ] Booking domain working with a start time and a duration, unaware of the reason
- [ ] Rules: no overlap per practitioner, none simultaneous per patient, inside availability
- [ ] Book, reschedule and cancel; cancelled appointments are kept
- [ ] Agenda query taking the `practitionerId` as a parameter — no authentication until stage 8
- [ ] Tests: overlaps at the boundaries, the same patient with two practitioners at the same time, same-day appointment

### Stage 5 — Check-in and automatic cancellation
- [ ] Add `@EnableScheduling`
- [ ] Confirm check-in, and suspend the automatic cancellation
- [ ] `@Scheduled` task every minute: 30 minutes without check-in, 24 hours when suspended
- [ ] Tests with a fixed `Clock` for the three cases and for an already attended appointment

### Stage 6 — Practitioner time off
- [ ] Record time off for a whole day or for a time range
- [ ] Cascading cancellation of the affected appointments **without** a confirmed check-in
- [ ] Report of the appointments with a confirmed check-in that need manual handling
- [ ] Tests: partial time off, full-day time off, an appointment with a confirmed check-in left untouched

### Stage 7 — Minimal Thymeleaf views
- [ ] Add the `thymeleaf` dependency
- [ ] Agenda of the day per practitioner
- [ ] New appointment and rescheduling, with slots suggested by the interface
- [ ] Confirm check-in from the agenda

### Stage 8 — Security
- [ ] Add the `security` dependency, with the `app_user` table and a seed migration
- [ ] Login for the views, and stateless authentication for `/api/**`
- [ ] `RECEPTION` and `PRACTITIONER` roles, with a `CurrentSession` api in the module
- [ ] Foreign key from `app_user.practitioner_id` to the practitioner table
- [ ] Reception queries any practitioner; a practitioner is restricted to their own agenda,
      with the received parameter ignored
- [ ] Tests: 401 without credentials, a practitioner cannot read another practitioner's agenda

A working version of this stage already existed and was removed; see D-04 and D-14. The
design is settled, so it is mostly a matter of bringing it back where it has something to
protect.

---

## Explicitly postponed

These are not oversights: they are decided and waiting for their moment.

- **PostgreSQL** replacing H2, with `docker-compose` (see D-03).
- **ArchUnit** enforcing the module boundaries (see D-05).
- A user administration screen (see D-04).
- A `.gitattributes` pinning line endings, once the repository is used from another machine
  or from CI.

## Out of scope for v1

See `docs/context.md`, section "Planned evolution": patient file, arrival queue, clinical
history, attendance with more states, reminders, extra patient data and web booking.
