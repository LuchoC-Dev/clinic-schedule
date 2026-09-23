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

Use cases, in `appointment/application/`:

- [x] `AppointmentRepository` — the one external dependency: where appointments are kept
- [x] `BookAppointmentUseCase` — idempotent: a known id returns the existing appointment
- [x] `RescheduleAppointmentUseCase`, `CancelAppointmentUseCase`
- [x] `AgendaReader` — gathers what could collide, shared by booking and rescheduling
- [x] `AppointmentNotFoundException`; each `Command` is a record nested in its use case
- [x] 15 tests against an in-memory port, still without Spring

Persistence, in `appointment/persistence/`:

- [x] `V1__appointment.sql` — `uuid` ids, the slot as start plus duration, status check
- [x] `AppointmentRow` — the `@Entity`, separate from the aggregate, and the translation
- [x] `AppointmentRowRepository` — Spring Data, a detail of the package
- [x] `AppointmentJpaAdapter` — implements the port
- [x] `@Service` and `@Transactional` on the use cases, `BookingService` as a bean in
      `AppointmentConfiguration` so the domain keeps no reference to Spring (D-21)
- [x] 8 tests against the real schema and 6 end-to-end flow tests. 64 in total

Endpoints, in `appointment/web/`, with the `validation` dependency now that something uses it:

- [x] `POST /api/appointments` — books; 201 with `Location`
- [x] `POST /api/appointments/{id}/reschedule`, `POST /api/appointments/{id}/cancel`
- [x] `GET /api/appointments?practitionerId=…&date=…` — the day, in order, without cancelled ones
- [x] `ViewDayAgendaUseCase`
- [x] `BookAppointmentRequest`, `RescheduleAppointmentRequest`, `AppointmentResponse`
- [x] `shared/web/ErrorResponse` and a module-scoped `AppointmentExceptionHandler`: 400 with
      the offending fields, 404, 409 (D-23)
- [x] 11 tests over the real stack. 75 in total

**Verified with the application running:** booking answers 201, an overlap 409 naming the
practitioner and the slot, a back-to-back appointment 201, a missing reason 400 naming the
field, and the day agenda comes back in order.

Not here yet, by design: the availability check (weekly schedule, breaks, time off), which
belongs to `practitioner`. The duration still travels in the request until then (D-08).

### `patient` module `[x]`

Layered, with JPA mapped straight onto the entity (D-25). A flat package, so everything is
package-private and nothing outside the module can reach the service (D-24).

- [x] `V2__patient.sql` — `uuid` id, full name, optional typed document
- [x] `Patient` — the `@Entity`, guarding its own shape, with no setters
- [x] `IdentityDocument` + `DocumentType` — the document as a value object (D-26)
- [x] `PatientRepository`, `PatientService`
- [x] `POST /api/patients`, `PUT /api/patients/{id}`, `GET /api/patients/{id}`,
      `GET /api/patients?query=…` (name or national ID, one search box)
- [x] `PatientExceptionHandler` — 404, 409 for a repeated document, 400 for validation
- [x] 10 unit tests on the document rules and 18 over the real stack. 103 in total

**Verified with the application running:** registering with and without a national ID, two
patients with no national ID, two patients sharing a name, a repeated national ID answering
409, and searching by name and by national ID.

**Notes:**
- A plain `unique` constraint over the document pair gives the rule asked for, because both
  PostgreSQL and H2 allow repeated nulls in a unique constraint. The partial index this plan
  used to call for is not needed, which keeps the SQL portable.
- The document number is normalized — separators removed, letters upper-cased — so two
  spellings of the same document cannot both be stored.
- No delete endpoint: a patient with appointments cannot disappear without taking history
  with them, and nothing in v1 asks for it.
- No `api/` package yet. It appears when `appointment` uses it, which is the next step
  (D-14).

### `practitioner` module `[~]`

Layered, with the week's rules in plain classes (D-25, D-28). A flat package, everything
package-private.

Configuration, done:

- [x] `V3__practitioner.sql` — practitioner, reason, working_hours
- [x] `Practitioner` — the `@Entity`; guards unique reason names and a coherent week
- [x] `Reason` — its own id, a fixed duration for this practitioner
- [x] `WorkingHours` + `WeeklySchedule` — a stretch of a weekday, and a week whose stretches
      neither overlap nor meet; a break is the gap between two
- [x] `POST/GET /api/practitioners`, `GET/PUT /api/practitioners/{id}`,
      `POST /api/practitioners/{id}/reasons`, `PUT /api/practitioners/{id}/reasons/{reasonId}`,
      `PUT /api/practitioners/{id}/weekly-schedule`
- [x] Validation handling moved to a single `shared/web/ValidationExceptionHandler`
- [x] 20 unit tests on the rules and 13 over the real stack. 136 in total

**Verified with the application running:** two reasons added, a repeated one in another case
answering 409, a week with a lunch break stored, stretches that meet answering 400 with an
explanation and leaving the previous week in place, and the detail read back in order.

Next — the connection with `appointment`, which closes D-08:

- [ ] `practitioner/api/`: the duration of a reason, and whether a slot fits the week
- [ ] `appointment` resolves the duration from the reason instead of receiving it
- [ ] Booking outside the practitioner's hours is refused
- [ ] Tests of `appointment` updated to book with real practitioners and reasons

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

- **PostgreSQL** replacing H2, with `docker-compose` (see D-03), which is also what makes
  the overlap check safe under concurrency: an exclusion constraint over a time range, which
  H2 cannot express (see D-21).
- **ArchUnit** enforcing the module boundaries (see D-05).
- A user administration screen (see D-04).
- A `.gitattributes` pinning line endings, once the repository is used from another machine
  or from CI.

## Out of scope for v1

See `docs/context.md`, section "Planned evolution": patient file, arrival queue, clinical
history, attendance with more states, reminders, extra patient data and web booking.
