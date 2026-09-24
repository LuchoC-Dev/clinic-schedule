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

### `appointment` module `[x]`

Built first, ahead of the order planned at the start: it is the only module with rules of its
own, so its domain was built first to check that the rules close. The domain, in
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

The availability check and the duration now come from `practitioner` (D-29). Time off is
still to come, in its own stage.

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

Connection with `appointment` (D-30):

- [x] `patient/api/PatientReader.exists`, implemented by the package-private service
- [x] `appointment` asks through `PatientRegistryPort`, answered by an adapter in
      `appointment/external/`
- [x] Booking an unregistered patient answers 400
- [x] Appointment tests book with patients registered through the patient endpoints.
      161 in total

**Verified with the application running:** a made-up patient id answering 400 and a
registered patient booked with 201.

No foreign key from `appointment` to `patient`: each module owns its tables, and the reference
is checked through the api (D-30).

### `practitioner` module `[x]`

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

Connection with `appointment`, which closes D-08 (D-29):

- [x] `practitioner/api/PractitionerReader` — the duration of a reason, and whether a span
      fits the week; `WeeklySchedule.covers` answers the second
- [x] `appointment` asks through its own `PractitionerSchedulePort`, answered by an adapter in
      `appointment/external/`, the only place importing from `practitioner`, and only `api/`
- [x] The duration follows from the reason; it is no longer sent
- [x] A slot outside the practitioner's hours, or across a break, answers 409; a reason the
      practitioner does not have answers 400
- [x] Rescheduling moves the start and keeps the booked duration
- [x] Tests of `appointment` book with real practitioners, created through the practitioner
      endpoints. 158 in total

**Verified with the application running:** an ecografía booked with its 45 minutes resolved
from the reason, a slot across the lunch break answering 409 with the span it asked for, the
last slot before the break accepted, and a day the practitioner does not work answering 409.

### Check-in and automatic cancellation `[x]`
- [x] `V4__appointment_check_in.sql` — `checked_in_at` and `auto_cancellation_suspended`, by `ALTER`
- [x] The rules in `Appointment`: check-in, late-arrival notice, when it is overdue (D-31)
- [x] `CheckInUseCase`, `SuspendAutoCancellationUseCase`, `CancelOverdueAppointmentsUseCase`
- [x] `OverdueAppointmentsJob` every minute, `@EnableScheduling` in the module's configuration,
      off in the tests with `app.scheduling.enabled=false`
- [x] `Clock` bean in `shared/TimeConfiguration`, with the fixed time zone
- [x] `POST /api/appointments/{id}/check-in`, `POST /api/appointments/{id}/suspend-auto-cancellation`
- [x] Tests with a fixed clock for every deadline and for an already attended appointment. 192 in total

**Verified with the application running:** the real job cancelled an appointment that had
started 40 minutes earlier without a check-in, and left alone one whose patient had checked in
and one whose patient had warned they would be late.

### Practitioner time off `[x]`
- [x] `V5__practitioner_time_off.sql`, `TimeOff` and `AvailabilityPolicy` in `practitioner` (D-32)
- [x] `practitioner/api/TimeOffWriter`, called from `appointment` through its port
- [x] `RecordTimeOffUseCase`: cancels what the absence touches **without** a check-in, reports
      the rest as needing manual handling, in one transaction
- [x] `POST /api/time-off` — a whole day, or part of one
- [x] Booking during an absence is refused
- [x] Tests: partial and whole-day absences, boundaries, an arrived patient left untouched,
      another practitioner and the next day left alone. 211 in total

**Verified with the application running:** an absence from 10:30 to 13:00 cancelled the 12:00
appointment, reported the 11:00 one whose patient had arrived, left 09:30 and 15:00 alone; a
booking at 12:30 answered 409 and one at 13:00 was accepted.

### Minimal Thymeleaf views `[x]`
- [x] `thymeleaf` dependency
- [x] Agenda of the day per practitioner, with check-in, late-arrival notice, move and cancel
- [x] Booking and moving, with times suggested every 15 minutes and any other time accepted
- [x] Patients: search and register
- [x] `SuggestSlotsUseCase`, `FindAppointmentUseCase`; names read through the modules' api
- [x] Refusals shown in Spanish (`ScreenMessages`); post/redirect/get everywhere (D-33)
- [x] 5 unit tests on the suggestions and 13 over the screens. 229 in total

**Verified with the application running:** every screen rendered empty and with data; a
patient registered and an appointment booked from the screens; the agenda showed the patient's
name and document; for a Tuesday with a lunch break the booking screen offered exactly the 30
free quarters, 12:30 included and 12:45 — which would cross the break — left out.

### Security `[x]`
- [x] `security` dependency; `V6__app_user.sql`, without a foreign key to the practitioner (D-30)
- [x] Login form for the screens, basic authentication for `/api/**`; CSRF on the screens' forms
- [x] `RECEPTION` and `PRACTITIONER`; `security/api/CurrentUserReader`, failing closed
- [x] `ViewDayAgendaUseCase` restricts a practitioner to their own agenda through
      `AgendaAccessPort`, ignoring the parameter (D-04)
- [x] A practitioner's agenda screen shows only them and no actions; their routes are closed anyway
- [x] Development data in `db/seed`, a Flyway location a real deployment drops (D-34)
- [x] 15 tests with the seeded users. 244 in total

**Verified with the application running:** an anonymous visit sent to the login form; a wrong
password sent back with an error; reception logged in and saw both practitioners with their
actions; Dra. Arias logged in, asked for Dr. Benítez's agenda and got her own with no actions,
and got 403 on the patients screen and on the patients API.

---

**v1 is complete.** Everything in the context's scope for the first version is built.

### After v1: agenda views and the booking screen `[x]`
- [x] Booking screen as a single form: refreshing keeps patient, time and note; a single match
      is chosen; a refusal names what is missing (D-35)
- [x] `ViewAgendaUseCase` replaces `ViewDayAgendaUseCase`: one practitioner or everyone, over a
      span of days, with the access rule of D-04 unchanged
- [x] `ViewPatientAppointmentsUseCase`: a patient's appointments, cancelled ones included
- [x] Range reads in `AppointmentRepository` and its JPA adapter
- [x] Agenda screen: everyone's day with a practitioner column, a week Monday to Sunday,
      previous/today/next; actions return to the view they came from
- [x] Patient appointments screen at `/agenda/patients/{id}`, reception only, linked from the
      patients screen and the agenda
- [x] 7 unit tests on the use cases, 3 on the adapter, 11 over the screens and security. 265 in total

**Verified with the application running:** the case reception reported — a time chosen, a
name typed and "Reservar" pressed — came back with "Elegí un paciente." and both the time and
the only matching patient already chosen, and booked on the second press; everyone's Friday
showed Dra. Arias's and Dr. Benítez's appointments with their names; Dra. Arias's week showed
Monday to Sunday; cancelling from everyone's week returned to it; Juan Gómez's appointments
listed the cancelled one; logged in as Dra. Arias, everyone's week showed only her own, and a
patient's appointments answered 403.

Follow-up after reception tried it:

- [x] Everyone's agenda offers a "Nuevo turno" button per practitioner; the first version had
      none there, and booking looked impossible
- [x] Booking screen refreshes by itself when the day or the reason changes, and opens with the
      first reason chosen (D-36)
- [x] A new patient can be typed on the booking screen and is registered when booking, through
      `patient/api/PatientWriter` (D-37)
- [x] 6 more tests over the booking screen. 271 in total

**Verified with the application running:** everyone's Friday offered a button for each
practitioner; Dr. Benítez's booking screen opened with a reason chosen and 24 free times;
searching DNI 30111222 chose Ana Pérez; a new patient with a DNI was registered and booked in one
press; the same DNI typed for someone else was refused in Spanish, keeping what had been typed.

### After v1: protections `[x]`
- [x] Lengths checked by `Patient`, `IdentityDocument` and `Appointment`: no screen answers 500
      for a long name, document number or note (D-38)
- [x] Document formats per type: DNI 7 or 8 digits, LE/LC 6 to 8, CI/passport letters and
      digits; a full name needs a letter
- [x] Booking and moving from now on and up to a year ahead; suggestions leave out times gone
- [x] Check-in and late-arrival notice only on the appointment's day; no time off for a day
      already gone; a reason lasts at most 8 hours
- [x] Malformed bodies, wrong types and missing parameters answered in the API's error body
- [x] `FixedClockConfiguration` for the tests, so fixed dates never turn into the past
- [x] A practitioner asking for another agenda gets 403 instead of their own (D-39)
- [x] Security tests for what had none: an inactive user, a practitioner user without a
      practitioner, the routes closed to practitioners one by one, and logging out
- [x] 45 more tests. 316 in total

**Verified with the application running:** the same probe of wrong data that had got through —
DNI 544, a 12-digit DNI, an LE with letters, a name of symbols, a booking in 2025 and in 2099, a
check-in twelve days early, a 100 000-minute reason, time off in 2020 — was refused with a
message naming the rule; the five screen inputs that answered 500 now come back as a message;
malformed JSON, a malformed id and a missing parameter answer in the usual error body. Dra.
Arias got 200 for her own agenda and 403 for Dr. Benítez's, both on the API and on the screen,
and her agenda screen with no one asked for still opened on her own.

### After v1: line endings `[x]`
- [x] `.gitattributes`: LF for every text file, CRLF for the Windows scripts (D-40)

**Verified with:** `git ls-files --eol` showing LF in the index and the working tree for every
file but `mvnw.cmd`, and a commit without the "LF will be replaced by CRLF" warnings.

### After v1: the appointment module laid out as its hexagon `[x]`
- [x] `domain/exception/`; `application/usecase/`, `port/`, `exception/`; `adapter/in/http/`,
      `screen/`, `scheduling/`; `adapter/out/persistence/`, `external/` (D-41)
- [x] `shared/web/` renamed `shared/http/`
- [x] Tests moved to the same packages; 65 files moved with their history, no behaviour changed

**Verified with:** the 316 tests passing unchanged.

### After v1: the layered modules split by layer `[x]`
- [x] `patient`, `practitioner`, `security`: `presentation/http/`, `presentation/screen/`,
      `service/`, `entity/`, `persistence/`, besides `api/` (D-42)
- [x] What crosses a layer made public, and no more
- [x] `archunit-junit5` 1.5.0 and `ArchitectureTest`: module boundaries for all four modules,
      layer access in the layered ones, only the service changes an entity
- [x] 48 files moved with their history
- [x] `user` separated from `security`: `UserAccountReader`, `UserAccount` and `Role` in its api,
      `security` logs users in through it (D-43)
- [x] 326 tests in total

**Verified with:** the 316 tests passing unchanged, and each architecture rule failing when a
violation of it was planted: another module importing `PatientService`, a controller holding the
repository, a controller renaming a patient, and `security` holding the user entity. Logging in
with the seeded users, by form and by basic authentication, goes through the user module.

### After v1: PostgreSQL `[x]`
- [x] PostgreSQL 18 with `docker-compose.yml`; the driver and `flyway-database-postgresql`; H2
      kept for the integration tests only (D-44)
- [x] `db/vendor/postgresql/V7`: exclusion constraints, no overlap per practitioner or per patient
- [x] The persistence adapter flushes on save and answers a constraint refusal as the usual 409
- [x] Unit tests (`*Test`, Surefire, `mvnw test`) apart from integration tests (`*IT`, Failsafe,
      `mvnw verify`); ten integration tests renamed
- [x] `AppointmentOverlapPostgresIT` with Testcontainers, skipped without Docker. 170 unit and
      160 integration tests, 330 in total

**Verified with:** `mvnw test` running the 170 unit tests with no database; `mvnw verify` running
the 160 integration tests on H2 and the four PostgreSQL ones on a container. With the application
on the docker-compose PostgreSQL: the eight migrations applied, both constraints present; a
booking answered 201 and an overlap 409; ten simultaneous bookings of one slot left exactly one
appointment, the other nine answered 409 and none 500; after a restart the data was still there
and Flyway had nothing to migrate.

### After v1: the agenda and booking flow `[x]`
- [x] The agenda's practitioner, day and view apply as they change; the heading says whose
      agenda it is (D-45)
- [x] One "Nuevo turno" button; the practitioner is chosen on the booking screen, and changing it
      keeps what was chosen
- [x] "No atiende ese día" told apart from "no quedan horarios", on booking and on moving
- [x] 3 unit and 7 integration tests. 340 in total

**Verified in a browser** against the application on PostgreSQL, following the reported flow:
choosing Benítez and then Arias on the agenda reloaded it each time, with the heading and the
booking button following; "Nuevo turno con Dra. Laura Arias" opened her booking with fifteen free
times; switching to Benítez there said "Dr. Martín Benítez no atiende ese día" (a Thursday);
"Nuevo turno" from everyone's agenda asked for the practitioner, and choosing Arias brought her
times. Changing the view and the day on the agenda reloaded it as well.

### After v1: practitioners and time off on the screens `[x]`
- [x] `/practitioners` and `/practitioners/{id}`: register, rename, reasons, the week as a table
      saved whole, the coming absences (D-46)
- [x] `/time-off` in the appointment module: the absence form, answering with what was cancelled
      and who needs handling by hand
- [x] Absences of several days, on the screen and in the API (`untilDate`); the rule in
      `RecordTimeOffUseCase.Command.of`
- [x] `TimeOffInThePastException`; a practitioner's name checked in the entity
- [x] 5 unit and 17 integration tests. 362 in total

**Verified in a browser** against the application on PostgreSQL: a practitioner registered, a
reason added, a week of two stretches saved; an appointment booked on the Friday; an absence from
Friday 25 to Monday 28 recorded, which answered "Turnos cancelados (1): vie 25/09 10:00 …" and
listed "del vie 25/09/2026 al lun 28/09/2026"; a day already gone refused in Spanish.

**v1 is usable end to end from the screens.**

---

## v2 — The clinical record

Scope in `docs/context.md`, section "Scope of v2". Delivered in parts; each one is usable on its
own.

### Part 1 — Consultations and the clinical record `[x]`
- [x] `docs/context.md`: the v2 scope; two stale v1 passages fixed (the typed document, time off
      of several days)
- [x] `consultation` module, hexagonal (D-47): `Consultation` with free-text notes and typed
      items (check, prescription, study order), immutable once sent; `V8__consultation.sql`
- [x] `AttendancePort` answered through `appointment`'s first `api/`, `AppointmentReader`,
      implemented by a driving adapter in `adapter/in/external` (D-48)
- [x] `ClinicalRecordAccessPolicy`: a consultation within the access period, or a visit today;
      the period in `app.clinical-record.access-period` (D-49)
- [x] 403 for whatever one may not see, recorded as a rule for the whole application (D-50)
- [x] `POST /api/consultations`, `GET /api/clinical-records/{patientId}`; the routes are the
      practitioners' only (D-51)
- [x] Screens: the record, and the consultation view with the form beside it; rows of items
      without JavaScript, "Más renglones" for three more; the practitioner's agenda links today's
      patients to their record and offers "Empezar consulta" once they arrived
- [x] 16 unit and 20 integration tests. 194 unit and 204 integration tests, 398 in total

**Verified with:** each access rule failing its tests when broken on purpose (everyone may read,
recording before the arrival, any practitioner's appointment counting); `mvnw verify` with the
PostgreSQL tests running V8 on a container.

**Verified in a browser** against the application on a throwaway PostgreSQL: Dra. Arias's agenda
linked both of today's patients to their record and offered "Empezar consulta" only for the one
who had arrived; "Más renglones" brought the form back with eight rows and the notes kept; a row
with only a kind was refused in Spanish, keeping it; the consultation with a check, a prescription
and a study order was recorded and shown in the record; the patient not yet arrived opened the
record but not the form. Dr. Benítez and reception got 403 on both screens and on the API.

**Stage notes:**
- The first run of the unit tests caught `List.of().contains(null)` throwing instead of answering:
  the domain checks for missing items with a stream.
- The browser caught "Más renglones" adding nothing when few rows were filled; it now adds three
  to the rows on screen, and the integration test counts them.

### Part 2 — Addenda `[ ]`
### Part 3 — Checks whose result arrives later `[ ]`
### Part 4 — Attachments `[ ]`

---

## Explicitly postponed

These are not oversights: they are decided and waiting for their moment.

- A user administration screen (see D-04); it has a module to land in since D-43.
- Registering a new patient and booking them in **one transaction** from the booking screen;
  today they are two, and a refused booking leaves the patient registered (see D-37).

## Out of scope for v1

See `docs/context.md`, section "Planned evolution": arrival queue, attendance with more states,
reminders, extra patient data and web booking. The clinical record is v2, above.
