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

## v2

v2 is built in phases, each one closed on its own. Which phase comes next is decided when the one
before is closed; the candidates are in `docs/context.md`, section "Planned evolution".

### Phase 1 — The clinical record `[x]`

Scope in `docs/context.md`, section "v2, phase 1 — the clinical record". Delivered in parts; each
one is usable on its own.

#### Part 1 — Consultations and the clinical record `[x]`
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

#### Part 2 — Addenda `[x]`
- [x] `Addendum` inside the `Consultation` aggregate: `withAddendum` returns a new consultation,
      with nothing already written changed; in order, each one once (D-52)
- [x] `AddAddendumUseCase`: any practitioner with access to the record, at any time; an unknown
      consultation is a 403; a retry with the same id adds nothing
- [x] `V9__consultation_addendum.sql`, and a version on the consultation: two addenda at the same
      time do not overwrite each other, the second is a 409
- [x] `POST /api/consultations/{id}/addenda`; the record shows each consultation's addenda
- [x] On the record screen, the addenda under each consultation and a form to add one
- [x] 5 unit and 10 integration tests. 199 unit and 214 integration tests, 413 in total

**Verified with:** the concurrency test failing without the version, and the access tests failing
with the access rule skipped; `mvnw verify` with the PostgreSQL tests running V9 on a container.

**Verified in a browser** against the application on a throwaway PostgreSQL: an empty addendum
was refused in Spanish; Dra. Arias's addendum appeared under her consultation after two of Dr.
Benítez's, sent through the API once he had an appointment with the patient that day, each with
its time and author; the prescription above stayed as written. Reception got 403.

**Stage notes:**
- The clinical record is a concept apart from the consultation: the access rule is the record's,
  the addenda are the consultation's. The module is still called `consultation`; it is renamed
  `clinicalrecord` in part 3, when checks with a later result make the record gather more than
  consultations.
- The final single-page applications must keep what was typed when an addendum answers 409, and
  send it again, by hand or by themselves (D-52).

#### Part 3 — Checks `[x]`

##### 3a — Checks and their results `[x]`
- [x] The `consultation` module renamed `clinicalrecord`, as D-52 planned: no behaviour changed
- [x] `Check`, an aggregate of its own: done during a consultation or without one, its result at
      once or later, `CheckResult` in a class of its own (D-53)
- [x] The `CHECK` item leaves the consultation; `V10__clinical_check.sql`, starting from an empty
      database
- [x] Checks sent with the consultation, in one transaction; `POST /api/checks` for one without a
      consultation, only for a patient who arrived today
- [x] `POST /api/checks/{id}/result`, by any practitioner with access to the record; a second
      result, or two at the same time, is a 409
- [x] Doing a check counts as seeing the patient for the access period, like a consultation
- [x] The record answers `{consultations, checks}`, each consultation with its checks; on the
      screen, checks under their consultation and alone by time, with the form for a pending result
- [x] 9 unit and 10 integration tests. 208 unit and 224 integration tests, 432 in total

**Verified with:** each of these failing its tests when broken on purpose — a second result
accepted, a check not counting as seeing the patient, the version removed, the access rule skipped
for a result; `mvnw clean verify` with the PostgreSQL tests running V10 on a container.

**Verified in a browser** against the application on a throwaway PostgreSQL: Dra. Arias sent a
consultation with a prescription, a blood pressure with its result and a blood sample pending,
and recorded an electrocardiogram without a consultation; the record showed the consultation with
its two checks and the electrocardiogram on its own, both pending ones with the result form. Dr.
Benítez, with an appointment with the patient that day, recorded the blood result under his name,
and saw no "Empezar consulta".

**Stage notes:**
- A development database from before V10 must be wiped (`docker compose down -v`): V10 refuses
  the check items it may hold, and none are carried over (D-53).
- The record's answer changed shape, from a list of consultations to `{consultations, checks}`;
  no client used it yet.

##### 3b — Asking for access to a record `[x]`
- [x] `AccessGrant` and `AccessGrantPolicy`: only for a pending check, approved automatically,
      lasting `app.clinical-record.access-grant.period` (a year), a reason required or not by
      `app.clinical-record.access-grant.reason-required` (required) (D-54)
- [x] A valid grant is a third way to read a record; it does not allow recording
- [x] `V11__record_access_grant.sql`; every grant kept, and shown in the record
- [x] `GET /api/checks/pending` and `POST /api/checks/{id}/access-grants`
- [x] Screens: "Resultados pendientes" for practitioners, with "Ver historia" or "Pedir acceso";
      "Accesos autorizados" in the record
- [x] `ClinicalRecordAccessReader` a component, given to the use cases
- [x] 6 unit and 7 integration tests. 214 unit and 231 integration tests, 445 in total

**Verified with:** each of these failing its tests when broken on purpose — grants ignored by the
access rule, a grant for a check that has its result, grants that never expire — the first two
failing the integration tests as well; `mvnw clean verify` with the PostgreSQL tests running V11.

**Verified in a browser** against the application on a throwaway PostgreSQL: Dr. Benítez, with no
appointment with the patient, found Dra. Arias's pending blood sample under "Resultados
pendientes", seeing only the patient and the check; asking without a reason was refused in
Spanish; with one, he landed on the record ("Acceso autorizado hasta el 25/09/2027."), reported
the result, and the record showed the grant under "Accesos autorizados"; the list was then empty.

##### 3c — Orders `[x]`
- [x] `CheckOrder`: issued in a consultation, open until one check fulfils it or its author
      cancels it with a reason; the order names the check that fulfilled it (D-55)
- [x] The `STUDY_ORDER` item leaves the consultation; `V12__check_order.sql`, starting from an
      empty database again
- [x] Orders sent with the consultation; a check, in a consultation or not, may name the open order
      it fulfils, and without a description it is what the order asks for
- [x] `POST /api/orders/{id}/cancel`, only by the author; a version and a unique constraint keep two
      checks from fulfilling one order
- [x] Screens: an "Órdenes" section in the consultation form, the open orders to choose from for a
      check, orders under their consultation with where they stand, "Anular orden" for the author
- [x] 6 unit and 9 integration tests. 220 unit and 240 integration tests, 460 in total

**Verified with:** each of these failing its tests when broken on purpose — the version removed
from orders, anyone allowed to cancel, a consultation's checks not fulfilling their order;
`mvnw clean verify` with the PostgreSQL tests running V12.

**Verified in a browser** against the application on a throwaway PostgreSQL: Dra. Arias issued two
orders in a consultation and cancelled the X-ray with its reason; Dr. Benítez, seeing the patient
that day, recorded a check choosing the blood-test order, with no description, and it took the
order's; the record showed the X-ray order cancelled with its reason and the blood test fulfilled
with its time, and offered him no "Anular orden".

**Stage notes:**
- A development database from before V12 must be wiped again (`docker compose down -v`).
- An order coming from elsewhere — on paper, or another practice — is left for later, with the
  validation it needs; meanwhile a check is recorded with no order, naming it in its description.

##### 3d — Following up one's orders `[x]`
- [x] `ListMyOrdersUseCase`: the user's own orders not cancelled, each open, waiting for its result
      or reported, and whether the user may still read the record (D-56)
- [x] `GET /api/orders/mine`, with no result texts; `V13`, an index on who issued an order
- [x] "Mis órdenes" for practitioners: what waits, the oldest first; what was reported in the last
      30 days, the newest first, and all of it on asking
- [x] 4 integration tests. 220 unit and 244 integration tests, 464 in total

**Verified with:** each of these failing its tests when broken on purpose — cancelled orders
listed, the 30-day window dropped; `mvnw clean verify` with the PostgreSQL tests running V13.

**Verified in a browser** against the application on a throwaway PostgreSQL: Dra. Arias issued
three orders; Dr. Benítez did the blood test without a result and the X-ray with one; "Mis órdenes"
showed the Doppler open and the blood test waiting for its result under "Pendientes", and the X-ray
under "Con resultado en los últimos 30 días", with who reported it and when and no result text;
"Ver todos los resultados" showed the list without the window.

**Stage notes:**
- Marking a result as reviewed is left for later: it is a state of each practitioner's own, and
  how it should work is not decided. The users already stand for their practitioners, so it does
  not wait for user administration.

#### Addenda on checks `[x]`

Brought forward from the postponed list after testing by hand: a wrong result had no way to be
corrected, and a check done without a consultation had no consultation to add a note to.

- [x] `Check.withAddendum`, the same rule as a consultation's: at the end, in order, each once,
      pending or reported alike (D-58)
- [x] `AddCheckAddendumUseCase`: any practitioner who may read the record; `POST /api/checks/{id}/addenda`
- [x] `V15__check_addendum.sql`; the check's version guards addenda as it guards the result, now
      refused as `CheckChangedException`, which the result's use case still answers as
      `ResultAlreadyRecordedException`
- [x] On the record screen, each check lists its addenda and takes a new one
- [x] 2 unit and 4 integration tests. 228 unit and 255 integration tests, 483 in total

**Verified with:** the tests failing with the access rule skipped and with the addenda not stored;
`mvnw clean verify` with the PostgreSQL tests running V15.

**Verified in a browser** against the application on a throwaway PostgreSQL: a check recorded with
the result "esto es falso, debe eliminarse", as found when testing by hand, got the addendum "El
resultado cargado es un error: corresponde a otro paciente. Repetir el estudio." from the record
screen, shown under it with its time and author, the result above it unchanged.

#### Part 4 — Attachments `[x]`
- [x] `Attachment`, an aggregate of its own: belonging to a consultation or a check
      (`AttachmentOwner`, a sealed type), with its name, kind, size and SHA-256 fingerprint (D-57)
- [x] The kind told by the file's first bytes — PDF, PNG, JPEG — never by its name; up to 10 MB
- [x] The content behind `AttachmentContentStore`: in the database today, in a table of its own
      (`V14`), and meant to move to an object store by changing the adapter alone
- [x] Attached at any time by any practitioner who may read the record; nothing is deleted
- [x] A check's result may be only its attachments: the text is optional when the check has one
- [x] `POST /api/consultations/{id}/attachments`, `POST /api/checks/{id}/attachments`,
      `GET /api/attachments/{id}`; 415, 413 and 403 as they apply
- [x] Screens: each consultation and check lists its files and takes new ones; the result form
      takes a file
- [x] 6 unit and 7 integration tests. 226 unit and 251 integration tests, 477 in total

**Verified with:** each of these failing its tests when broken on purpose — a download without the
access rule, any file accepted, a result with neither text nor file; `mvnw clean verify` with the
PostgreSQL tests running V14.

**Verified in a browser** against the application on a throwaway PostgreSQL: a text file named
`.pdf` was refused in Spanish; a PNG was attached to a consultation; a pending blood test got its
result as a PDF alone, shown as "Resultado: en el archivo adjunto"; both files, downloaded back
from PostgreSQL, had the same SHA-256 as the originals and as the one stored; Dr. Benítez, with no
access to the record, got 403 on both.

**Phase 1 of v2, the clinical record, is closed.** Consultations with their addenda, checks with
results now or later, orders, access asked to report a check, the follow-up of one's orders and
attached files, all usable from the screens and the API. What it left for later is listed below,
under "Explicitly postponed".

### Phase 2 — The arrival queue `[x]`

Scope in `docs/context.md`, section "v2, phase 2 — the arrival queue". Delivered in parts; each
one is usable on its own.

#### Part 1 — The read log of the clinical record `[x]`
- [x] `docs/context.md`: the scope of phase 2
- [x] `RecordReading`: who read whose record, when, by which access, and which attached file (D-59)
- [x] `ClinicalRecordAccessPolicy.readAccess`: by which access the record is open —
      `VISIT_TODAY`, `ACCESS_GRANT`, `SEEN_WITHIN_PERIOD` — the first in the rule's order
- [x] Recorded when the record is shown, through the API or on screen, and when a file is
      downloaded; in the same transaction, so nothing is read without a trace
- [x] `V16`: `record_reading`, only inserted, through `RecordReadingWriter`; nothing reads it yet
- [x] Not recorded: refusals, the pending-results list and "Mis órdenes"
- [x] 5 unit and 9 integration tests. 233 unit and 264 integration tests, 497 in total

**Verified with:** each of these failing its tests when broken on purpose — a record read without
saving the reading, a download recorded as a reading of the whole record, the rule's order swapped,
a refused download recorded; `mvnw verify` with the PostgreSQL tests running V16.

**Verified with the application running** on a throwaway PostgreSQL: Dra. Arias read the record of
a patient who had arrived and downloaded its PDF — `VISIT_TODAY`, the second with the file; Dr.
Benítez was refused both, which left nothing, then asked for access to the pending check and read
the record — `ACCESS_GRANT`; the pending list and "Mis órdenes" left nothing. In a browser, the
record screen and the file downloaded from it added one reading each.

#### Part 2 — Patient identity `[x]`
- [x] `IdentityStatus`: `ALIAS`, `DECLARED`, `VERIFIED`; every existing patient `DECLARED` (D-60)
- [x] A patient who gives no name, under a random alias such as NN-4821, unique, kept after they
      give their name and still found by the search
- [x] `Patient.changeIdentity`: the one way to change name and document; seeing the document
      verifies, and a verified identity is declared again when its name or document changes
- [x] `V17`: the state and the alias on `patient`, and `patient_identity_change`, with who and when
- [x] `POST /api/patients/unidentified`, `documentSeen` on registering and correcting,
      `GET /api/patients/{id}/identity-changes`
- [x] Screens: "Registrar sin datos (NN)", each patient's screen to correct and verify their
      identity with its history, and "Identidad no verificada" on the record and the consultation
- [x] `CurrentUserReader.username()`, to say who changed it
- [x] 12 unit and 13 integration tests. 245 unit and 277 integration tests, 522 in total

**Verified with:** each of these failing its tests when broken on purpose — a verified identity
kept whatever changes, a change not saved to the history, the alias not searched, the alias dropped
once the name is given, the record not warning; `mvnw verify` with the PostgreSQL tests running V17.

**Verified in a browser** against the application on a throwaway PostgreSQL: the two seeded patients
came out of V17 declared; reception registered NN-6951 and Dra. Arias, seeing them that day, read
"Identidad no verificada" on their record; reception gave the name and the DNI, ticked "Vi el
documento", and the record stopped warning; the history showed the change by `recepcion`, and
searching for NN-6951 found María Fernández; renaming her without ticking the box made her
unverified again, a second entry; ticking it with no document was refused in Spanish.

#### Part 3 — Absent instead of cancelled `[x]`
- [x] `ABSENT`: 30 minutes without a check-in, or at the end of the day after a late-arrival
      notice; the same sweep, `MarkAbsentAppointmentsUseCase` (D-61)
- [x] An absent appointment is a record: not cancelled, moved, checked in nor warned about; it
      frees its time
- [x] Shown on the agendas and the API as absent, with no buttons
- [x] It still opens the record that day, and records nothing: `AppointmentReader` answers what
      was not cancelled
- [x] The late-arrival notice named as such: `POST /api/appointments/{id}/late-arrival-notice`
- [x] `V18`: the status check widened; automatic cancellations from before stay cancelled
- [x] 3 unit and 6 integration tests, and the day-of-appointment tests brought to the new rule.
      248 unit and 283 integration tests, 531 in total

**Verified with:** each of these failing its tests when broken on purpose — the sweep cancelling
again, the notice waiting 24 hours, the agenda or the api leaving absent appointments out, an
absent appointment cancelled, the agenda offering its buttons; `mvnw verify` with the PostgreSQL
tests running V18.

**Verified with the application running** on a throwaway PostgreSQL, with the real job: of
appointments moved back in time, the one without a check-in 40 minutes after its start and the one
warned yesterday became absent; the one checked in and the one warned today stayed booked, a
minute later too. Once absent, its slot took a new booking that PostgreSQL had refused while it was
booked; cancelling, checking in and warning it answered 409, and the old route 404. In a browser,
the agenda showed "Ausente" with no buttons, today and yesterday. Dra. Arias read the record of a
patient absent today, and could not record a consultation (403).

#### Part 4 — Setting up queues `[x]`
- [x] A new module, `arrival`, with its own architecture: event sourcing and CQRS, split by
      subdomain — `eventstore/`, and `setup/` with `domain/`, `command/`, `query/`,
      `projection/`, `http/` and `screen/` (D-62)
- [x] `Queue` rebuilt from its events — created, renamed, a practitioner joined or left — and
      `QueueSetupService`, the only way in: a name nobody else has whatever the case, a
      practitioner serving one queue at most; a queue with nobody serving it stays
- [x] `V19`: `arrival_event`, keyed by stream and version, with who and when; the projections
      `arrival_queue` and `queue_membership`, written in the same transaction, whose keys guard
      both rules when two writers race
- [x] `POST`/`GET /api/queues`, `GET`/`PUT /api/queues/{id}`, `POST /api/queues/{id}/practitioners`,
      `DELETE /api/queues/{id}/practitioners/{practitionerId}`; screens `/queues` and `/queues/{id}`,
      for reception
- [x] `ArchitectureTest`: `arrival` through its api like every module; its domain with no
      framework; its reads never on the events
- [x] 12 unit tests (9 on the domain, 3 architecture rules) and 19 integration tests. 260 unit and
      302 integration tests, 562 in total

**Verified with:** each of these failing its tests when broken on purpose — the service letting a
practitioner into a second queue, the projection telling names apart by case, the commands not
updating the projection, every event appended as the first, a replay accepting a practitioner
twice, the reads going to the events; `mvnw verify` with the PostgreSQL tests running V19.

**Verified with the application running** on a throwaway PostgreSQL: two queues set up, one
without a room; "guardia" refused beside "Guardia"; Dra. Arias refused a second queue; a
practitioner refused the routes (403). Two requests at once putting Dra. Arias into two queues
got a 200 and a 409, and the refused one left no event behind. The events read back in order, each
with its JSON and `recepcion`. In a browser, the queues screen listed each with who serves it, a
queue was created and Dr. Benítez moved into it from another, which stayed with nobody serving it;
a taken name was refused in Spanish.

#### Part 5a — Queues at work, without appointments `[x]`
- [x] `session/`: each opening of a queue is a stream of events, from its opening to its closing
      (D-63); `QueueSession`, its `Entry`s, `QueueSessionService`, the only way in
- [x] `CallOrder`: emergencies by when they were declared, then everyone by arrival
- [x] Opening, closing, reopening — a new session —, closing by itself at the end of the day unless
      reception keeps it open; a job every minute, recorded as `system`
- [x] Reception brings patients in — found, or registered without a name —, as emergencies or not;
      makes a waiting one an emergency; says one left
- [x] The practitioner: "Llamar siguiente", "Terminar", "No ingresó" — passed over once, out after
      the third call, `app.arrival.calls-before-no-show`
- [x] `V20`: the session's projection, with the key that keeps one session open per queue
- [x] `arrival/api/ArrivalReader`, and `AttendanceAdapter` answering from the queue too: the
      practitioners of a queue read the record of whoever waits in it, and only whoever called them
      writes it that day
- [x] API and screens: `/arrivals` for reception, `/my-queue` for the practitioner
- [x] 18 unit and 20 integration tests. 278 unit and 322 integration tests, 600 in total

**Verified with:** each of these failing its tests when broken on purpose — emergencies not first,
whoever did not come in not passed over, one call more before a no-show, closing that leaves the
waiting ones waiting, a queue kept open that closes by itself, a practitioner with two patients
called, waiting in a queue that opens no record, anyone in the queue able to write it, a closed
session left as the open one; `mvnw verify` with the PostgreSQL tests running V20.

**Verified in a browser** against the application on a throwaway PostgreSQL: reception opened
Dra. Arias's queue and brought in Ana and Beto, Carla as an emergency, and a patient without a name,
NN-8177 — Carla first, then the others by arrival. Dra. Arias called Carla, started her
consultation from her queue and recorded it; finished; called Ana, who did not come in; the next
call went to Beto, and the one after back to Ana. Reception saw who was called by whom and who was
seen, and closed the queue: NN-8177, still waiting, was not seen. The session's thirteen events read
back in order, each by `recepcion` or `arias`.

#### Part 5b — Patients with an appointment, and the arrival leaves the appointment `[x]`

Step 1 — into the queue:

- [x] `AppointmentArrived`: into the queue of the appointment's practitioner, kept for them, on its
      day, while booked, the queue open (D-64)
- [x] `CallOrder`: emergencies, then appointments by the later of their time and the arrival, then
      everyone else; a patient kept for a practitioner is called and read by that one only
- [x] Reception gives a waiting patient to another practitioner, keeping their place, or lifts the
      reservation; the screen warns of whoever nobody can call
- [x] A cancelled appointment leaves its patient in the queue for anyone:
      `appointment/api/AppointmentArrivals`, implemented by the queues, told in the same transaction
- [x] `V21`: the appointment, its time and the reservation on each entry
- [x] 11 unit and 9 integration tests. 288 unit and 331 integration tests, 619 in total

**Verified with:** each of these failing its tests when broken on purpose — appointments after who
came without one, an appointment placed by its time alone, anyone calling a patient kept for someone,
a cancellation telling nobody, a patient kept for someone read by all the queue, nobody ever
uncallable, a patient given to someone outside the queue.

**Verified in a browser** against the application on a throwaway PostgreSQL: Ana Torres, found by her
name, came in with "Llegó con turno 20:00", kept for Dra. Arias and ahead of Beto, who came without an
appointment; Dra. Arias left the Guardia and the screen said nobody could call Ana; "Dar a" gave her
to Dr. Benítez in the same place; her appointment cancelled, she stayed first, waiting for nobody. The
session's events read back as `AppointmentArrived`, `PatientReassigned`, `ReservationLifted`.

Step 2 — the arrival leaves the appointment:

- [x] "Llegó" on the agenda brings the patient into their practitioner's queue; the check-in of the
      appointment is gone, `CheckInUseCase` and its route with it (D-65)
- [x] `AppointmentArrivals` answers when each appointment's patient arrived; `AppointmentDayService`
      and `BookingService` are given it — no notice, no absence, no move once the patient came
- [x] The sweep, the time-off cascade, the agendas and the API ask the queues; `arrivedAt` in the API
- [x] `V22`: the check-ins recorded so far move to `appointment_check_in`, and `checked_in_at` leaves
      the appointment; `V101`: a queue for each seeded practitioner, out of the way of queues already
      there
- [x] Writing a record always follows a call now: the tests of the clinical record bring their
      patients in through `ArrivalFixture`
- [x] 283 unit and 330 integration tests, 613 in total; the check-in's own tests went with it

**Verified with:** each of these failing its tests when broken on purpose — the sweep marking absent
whoever arrived, the time-off cascade cancelling them, an arrived patient's appointment moved, a late
notice accepted after the arrival, the check-ins from before the queues forgotten, the arrivals
through the queues forgotten; `mvnw verify` with the PostgreSQL tests running V22.

**Verified with the application running** on a throwaway PostgreSQL: taken up to V21, given an
appointment checked in the old way, and started: V22 moved that check-in alone and dropped the
column. Given a queue of its own for Dra. Arias, V101 left it and set up Dr. Benítez's alone. In a
browser, reception pressed "Llegó" on Dr. Benítez's agenda for Carla Suárez: back on the same agenda,
"Llegó a las 19:33" with only "Cancelar" left. Dr. Benítez's agenda said "En tu cola"; from his queue
he called her, started her consultation and recorded it.

---

#### Part 6 — Screens that show themselves again, and the waiting room `[x]`
- [x] `QueueChanged`, published by every command of the queues and their sessions, and sent by
      `LiveQueues` over Server-Sent Events once the transaction commits (D-66)
- [x] `/queue-events`, for reception and the practitioners; `fragments :: live` swaps the region
      marked `id="live"` of reception's arrivals, the practitioner's queue and the waiting room
- [x] `/waiting-room`: every queue, whether it is open, how many wait and are being seen, no patient
      by name; reception's for now
- [x] 6 integration tests. 283 unit and 336 integration tests, 619 in total

**Verified with:** each of these failing its tests when broken on purpose — the screens told before
the change commits, the sessions telling nobody.

**Verified in two browsers at once** against the application on a throwaway PostgreSQL: Dra. Arias's
queue and the waiting room were open while her queue was opened and two patients brought in from
another desk; both showed each change without being reloaded, and the waiting room never named a
patient. On reception's arrivals, a search half-typed stayed while the list below it changed.

**Phase 2 of v2, the arrival queue, is closed.** Every reading of a clinical record is recorded;
patients who give no name are seen under an alias; an appointment whose patient never came is absent;
queues are set up and put to work, for patients with an appointment and without one, called in the
order one rule decides; the arrival left the appointment for the queues; and the screens follow the
queues as they change. What it left for later is listed below, under "Explicitly postponed".

### Phase 3 — Users and security `[x]`

Scope in `docs/context.md`, section "v2, phase 3 — users and security". Delivered in parts; each
one is usable on its own.

#### Part 1 — The admin and the users `[x]`
- [x] `docs/context.md`: the scope of phase 3
- [x] The `ADMIN` role, which reaches `/api/users/**` and `/users/**` alone; each role lands on its
      own screen, and the admins' screens have a header of their own (D-68)
- [x] `AppUser.create`: a username of 3 to 50 lower-case letters, digits, dots, hyphens or
      underscores; the practitioner for a practitioner's user alone; `PasswordPolicy`, 10 to 72
      characters counted in bytes
- [x] `UserAdministrationService`: a username never used twice, a practitioner with one active user
      at most, also on reactivating; an admin does not deactivate themselves
- [x] Deactivating and reactivating, never deleting; resetting a password
- [x] `app_user_change`: every admin action, with who and when, in the same transaction; shown on
      each user's screen and through the API
- [x] `UserDeactivated`, heard by `security` after the commit: the user's sessions end at once,
      through the `SessionRegistry`; the login form says why
- [x] `V23`: the role check widened, `app_user_change`; `db/vendor/postgresql/V24`: one active user per
      practitioner, a partial unique index; `V102`: `admin` in the development data
- [x] `GET`/`POST /api/users`, `GET /api/users/{id}`, `POST /api/users/{id}/deactivate`,
      `/reactivate`, `/password-reset`, `GET /api/users/{id}/changes`; screens `/users`, `/users/{id}`
- [x] `ArchitectureTest` takes `isX` for a getter, as `getX`
- [x] 20 unit and 22 integration tests. 303 unit and 358 integration tests, 661 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 34
breakages in all, among them the username's form, its length and its reuse, the practitioner of the
role, a new user inactive, a reactivation that did not reactivate, a reset that kept the old
password, the history not saved, the sessions not ended, reception reaching the users and the admin
reaching reception's routes and screens, the admin landing on the agenda, the hash in the answer, the
password counted in characters, and the partial index made plain or dropped; `mvnw verify` with the
PostgreSQL tests running V23, V24 and V102.

**Verified with the application running** on a throwaway PostgreSQL: taken to V101 by the previous
commit's build, given a patient, and started: V23 and V24 applied out of order, then V102, with the
existing users kept. Through the API the admin created `recepcion.b`, who read the patients; was
refused a second user for Dra. Arias (409), a short password (400) and deactivating themselves
(409); deactivated `recepcion.b`, who was then refused (401), could not give the name again (409),
and reactivated them with a new password. The admin was refused the patients and a clinical record,
and reception the users (403). In two browsers the admin landed on "Usuarios", created `gomez` for a
new practitioner and deactivated them while `gomez` had the agenda open: `gomez`'s next click went to
the login form, "Tu sesión se cerró: tu usuario fue desactivado", and logging in again was refused.

#### Part 2 — Passwords `[x]`
- [x] `V25`: `app_user.must_change_password`, false for the users already there; `PASSWORD_CHANGED`
      in the history (D-69)
- [x] A password an admin sets — creating or resetting — is temporary; `AppUser.changePassword`
      makes it the user's own, recorded by the user
- [x] `PasswordChangeService`: the current password, a new one that follows `PasswordPolicy` and is
      not the same
- [x] `POST /api/password` and `/password`, for every role, linked from every header; the screen
      asks for the new one twice, and ends the session once it is changed
- [x] `TemporaryPasswordFilter`: a user with the `TEMPORARY_PASSWORD` authority lands on `/password`
      and is kept there; on the API everything else is a 403 that says why
- [x] 2 unit and 9 integration tests; the tests that log in as a user an admin created change the
      temporary password first. 305 unit and 367 integration tests, 672 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 18
breakages: a new or reset password not temporary, a change that kept it temporary or was recorded as
the admin's or not at all, the current password not checked, the same one or a short one accepted,
the filter letting the API or the screens through, the login not landing on the change, no
temporary authority, the repetition not compared, the session kept after the change, the header link
gone, the screen or the API closed to some role, and the existing users made temporary by `V25`;
`mvnw verify` with the PostgreSQL tests running V25.

**Verified with the application running** on a throwaway PostgreSQL: taken to V102 by part 1's
commit, given `recepcion.b`, and started: V25 applied out of order and every user already there kept
their password. Through the API `recepcion.c`, just created, was refused the patients with the reason
(403), a wrong current password (400), changed it (204), and read the patients with the new one while
the temporary one no longer worked (401). In two browsers the admin reset `recepcion.b`'s password;
logging in with it landed on "Tu contraseña es temporal", "Llegadas" sent back there, a repetition
that did not match was refused in Spanish, and the change ended the session: "Contraseña cambiada:
ingresá con la nueva". Logging in again led to the agenda, and the user's history showed the admin's
reset and "Cambió su contraseña" by `recepcion.b`.

**Stage notes:**
- The first try of the live check found every user temporary: the jar in `target/` had been built by
  the planted-failure run with `V25` broken on purpose, and restoring the source does not rebuild it.
  Rebuilt clean and checked again on a new database; the jar is now rebuilt after every such run.

#### Fix — a live screen never shows what it no longer knows `[x]`
- [x] Found checking the waiting room before part 3: an open stream is not activity for the session,
      so after half an hour with no queue changing the session ran out, and the next change left the
      old region on screen, with nothing saying so
- [x] `fragments :: live`: when the page asked for has no `id="live"` region, or the stream fails for
      good, the whole page is loaded again (D-66, amended)
- [x] No test added: the script runs in the browser alone, where it was checked. 305 unit and 367
      integration tests, 672 in total

**Verified in browsers** against the application on a throwaway PostgreSQL, with sessions of one
minute. Before the fix, the waiting room left Dr. Benítez's queue shown closed after it was opened
once the session had run out. After it, the waiting room went to the login form on the next change;
reception's arrivals, showing Dra. Arias's queue, stayed as they were while only Dr. Benítez's queue
changed, which was still true, and went to the login form on the next change to hers. With the waiting
room open, restarting the application — which loses the sessions — brought the screen to the login form
within seconds.

#### Part 3 — The waiting-room user `[x]`
- [x] The `WAITING_ROOM` role: `/waiting-room` and `/queue-events`, nothing else on the screens or
      the API; straight there on logging in; reception may still open the waiting room (D-70)
- [x] A waiting-room session has no idle limit; every other one runs out as before
- [x] `V26`: the role check widened; `V103`: `sala` in the development data
- [x] "Sala de espera" among the roles the admin chooses
- [x] 1 unit and 7 integration tests. 306 unit and 374 integration tests, 680 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 12
breakages: the waiting room's session running out, every session never running out, the waiting room
landing on the agenda, the stream or the screen closed to it, the screen closed to reception or open
to the practitioners, the agenda, reception's screens or reception's API open to it, `sala` seeded as
reception, and a waiting-room user standing for a practitioner.

**Verified with the application running** on a throwaway PostgreSQL, with sessions of one minute:
taken to V102 and V25 by the previous commit, with a queue open, and started: V26 applied out of
order, then V103. `sala` landed on the waiting room, and two and a half minutes later, untouched,
still showed Dr. Benítez's queue opening; the agenda, the arrivals and the patients were refused, and
the API too. The admin created `sala.b` as "Sala de espera" on the screen; its first login went to
the password change, and the next one to the waiting room.

#### Part 4 — The admins read the readings of the clinical records `[x]`
- [x] `V27`: `record_reading.read_by`, the user, from now on; the readings from before have none; indexes
      on `read_at` and on `patient_id` with `read_at` (D-71)
- [x] `RecordReadingReader` and `ListRecordReadingsUseCase`: by patient — resolved by the patient
      module's search —, practitioner and days, the last week by default; the most recent first, 500 at
      most, saying when there were more
- [x] `GET /api/record-readings` and `/record-readings`, the admins' alone, "Lecturas" in their header
- [x] Who — practitioner and user —, whose — name and document —, when, by which access, and whether it
      was a file; never what was read, nor the file's name
- [x] Looking at the readings is not recorded, for now
- [x] 1 unit and 12 integration tests. 307 unit and 386 integration tests, 693 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 20
breakages: the user not stored or not required, the readings from before refused for lacking one, the
patient, practitioner and first-day filters ignored, the last day left out, a default of eight days,
the limit never noticed, the oldest first, the days in any order, a file never said, the API open to
the practitioners and the screen to reception or the practitioners, the missing user shown blank, the
header link, the error and the access unsaid. A 21st, dropping the early answer when no patient
matches, changed nothing: the query with no patient finds nothing either, so it only saves a query.
`mvnw verify` ran V27 on H2.

**Verified with the application running** on a throwaway PostgreSQL: taken to V103 and V26 by the
previous commit, where Dra. Arias, with an appointment with Ana Pérez that day, read her record; then
started: V27 applied out of order. Dra. Arias read it again and Dr. Benítez was refused. The admin found
both readings — the new one by `arias`, the old one "sin usuario registrado" —, by "Ana" through the
API and by default on the screen, and none for Dr. Benítez; a first day after the last was refused in
Spanish; reception, Dra. Arias and `sala` got 403.

#### Part 5 — Refused attempts `[x]`
- [x] `RecordRefusal`: who — user, and practitioner if any —, when, what — a record, a file, a
      clinical route —, whose record when known, the file, the route; `V28`: `record_refusal` (D-72)
- [x] A record or file refused to a practitioner, recorded by the use case, in a transaction of its
      own so it outlives the refusal's rollback; a file that does not exist too, whose unknown
- [x] A clinical route refused to a role — records, files, consultations, checks, orders; not the
      practitioner's queue —, said by the security module as `ClinicalRouteRefused` and recorded by
      the clinical record, with the patient when the route names their record; not a missing CSRF
      token, nor a request with nobody logged in
- [x] `GET /api/record-refusals` and `/record-refusals`, the admins' alone, "Rechazos" in their header;
      by patient, user and days, as the readings
- [x] 5 unit and 12 integration tests. 312 unit and 398 integration tests, 710 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 25
breakages: a refused record or file not saved, the refusal saved in the refused transaction, a
refused file without its patient, a refusal without patient, file, route or user, a long route not
cut, the route naming no patient, the API's or the screens' refusals unsaid, every route taken for a
clinical one, a missing token said, the route or the practitioner not stored, the user or the patient
filter ignored, the days in any order, the API open to the practitioners and the screen to reception
or the practitioners, the header link, the route and the error unsaid on the screen. A 26th, saying a
refusal with nobody logged in, changed nothing: Spring Security sends that request to log in and never
refuses it, so the check was dropped.

**Verified with the application running** on a throwaway PostgreSQL: taken to V27 by the previous
commit, where a refusal left nothing, and started: V28 applied out of order. Dr. Benítez refused Ana
Pérez's record and a file that does not exist, Dra. Arias refused a record she had no visit for,
reception refused her record through the API and on the screen, and the admin refused the
consultations: six refusals, each with whose record when known. Reception refused its practitioner's
queue and an anonymous request left none. The admin saw them all on "Rechazos", and reception's two by
searching the user.

**Phase 3 of v2, users and security, is closed.** Every person at the practice has a user of their
own, created by an admin, who reaches nothing else and never clinical content; users are deactivated,
never deleted, and lose access at once; a password an admin sets is temporary until its user changes
it; the waiting room's screen logs in as a user that sees it and nothing else, and the live screens no
longer show a queue they no longer know; and the admins read who read which clinical record and who
was refused one. What it left for later is listed below, under "Explicitly postponed".

### Phase 4 — The patient's extra data `[~]`

Scope in `docs/context.md`, section "v2, phase 4 — the patient's extra data". Delivered in parts;
each one is usable on its own. The technical design of each part is agreed before it is coded.

#### Part 1 — Date of birth and contact `[x]`
- [x] `docs/context.md`: the scope of phase 4
- [x] `PatientDetails`, apart from the identity, through `Patient.changeDetails`; registering takes
      them at once and records no change (D-73)
- [x] Date of birth: not in the future, not more than 130 years ago, against the `Clock`
- [x] `Phone`: 8 to 15 digits with an optional leading `+`, separators dropped; `Email`: not unique,
      at most 254 characters, kept in lower case; each refuses with `InvalidPatientDetailsException`
- [x] Whose each one is, the phone and the email apart: the patient's own, or a `Responsible` — a
      name and a `Relation`: mother, father, guardian, partner, child or someone else; never
      required, a minor included
- [x] `V29`: the details as columns of `patient`, with checks on the relation and on an owner with
      its value; `patient_details_change`, one row per detail that changed, with who and when
- [x] `POST /api/patients` takes the details; `PUT /api/patients/{id}/details`, `GET
      /api/patients/{id}/details-changes`; on the patient's screen, "Datos personales" and its history,
      and the same fields on registering, an alias patient included
- [x] Practitioners read the age beside the clinical record and the consultation — in years, in months
      below two, in days below one month — and `patient.birthDate` in `GET /api/clinical-records/{id}`,
      only once the record is granted; admins and practitioners are refused the rest (403)
- [x] 23 unit and 14 integration tests. 335 unit and 412 integration tests, 747 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 32
breakages in all: the phone's length at both ends and its separators, an owner without a phone or an
email, the email's case, length and dot, the responsible person's relation, letter and "neither or
both", a date of birth tomorrow or 131 years ago, the history missing the date, the phone's new owner,
the owner before or its user, the details not set on changing or on registering, the history not
saved, the summary, the API and the record answer without the date of birth, the API and the screen
dropping the owner or the details on registering, the screen's refusal and its labels, and the age's
months, days and singular. The database's checks on the owner are not reached by any test: the
value objects refuse the same first, so the checks only guard rows written outside the application;
they were tried by hand below. `mvnw verify` with the PostgreSQL tests running V29.

**Verified with the application running** on a throwaway PostgreSQL: taken to V103 by the previous
commit's build and given patients — verified, under an alias, and one with an identity change — then
started: V29 applied out of order, and every patient and history kept, with no details. Through the API
reception gave a patient a date of birth, a phone of his daughter's and his own email, typed with
separators and capitals and kept clean; the history showed three entries by `recepcion`; a short
phone, a date in the future and an owner without a relation were 400; the admin and a practitioner
were refused the patient and the history (403). The database refused an owner without a phone and an
unknown relation. In the browser reception registered a boy with his mother's phone and his father's
email, then made the phone his own: the history read "1155551234 (Madre: Sofía Ríos) → +5491166667777",
and a short phone came back as the message. Dra. Arias, with an appointment with the patient today, read
"76 años · nació el 20/05/1950" on the record, and neither the phone nor the email; the patient's screen
was refused to her and to the admin.

#### Part 2 — Health insurance `[x]`
- [x] `HealthInsurance`, one at most: the insurer's name, the member number and the plan, free text of
      100, 30 and 50 characters; a member number or a plan needs the insurer; no token kept (D-74)
- [x] None on the books is "Sin obra social cargada", never "private": nobody asked the patients
      already there
- [x] `V30`: three columns of `patient`; its changes in the same history, with the member number and the
      plan, before and after, in columns of their own
- [x] On registering, on `PUT /api/patients/{id}/details` and on the patient's screen, with its history
- [x] Practitioners read it beside the clinical record and the consultation, and as `patient.insurance`
      in `GET /api/clinical-records/{id}`, once the record is granted
- [x] 7 unit and 6 integration tests. 342 unit and 418 integration tests, 760 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 21
breakages in all: an insurance without its insurer, a member number or a plan alone taken for none,
each part one character too long, an insurer without a letter, the parts not trimmed, a change of
insurance not recorded, the member number before or the plan after lost from the history, the
insurance not stored, the summary, the record's answer and the API's body dropping it on changing and
on registering, the history's plan left out of the API, the screen dropping it, its label and its
plan, a patient with none shown as "Particular", and the member number hidden from the practitioner.
`mvnw verify` with the PostgreSQL tests running V30.

**Verified with the application running** on a throwaway PostgreSQL: taken to V29 by part 1's build and
given a patient with her son's phone and email, and their history; then started: V30 applied out of
order, and the patient, her details and their history kept, with no insurance. Through the API reception
gave her PAMI with its fourteen-digit member number, which the history showed as its own entry; a plan
alone was a 400, and the database refused a plan without an insurer. In the browser reception added the
plan: the history read "PAMI · afiliado 15012345678900 → PAMI · plan Básico · afiliado
15012345678900". Dra. Arias, with an appointment with her today, read "Obra social: PAMI · plan Básico ·
afiliado 15012345678900" beside the age on the record, and not the phone; a record she has no access to
was still refused.

#### Part 3 — Registering and booking in one transaction `[x]`
- [x] `BookNewPatientUseCase`: registers through `PatientRegistrationPort` and books through
      `BookAppointmentUseCase`, in one transaction: a refused booking leaves no patient behind, and a
      refused patient books nothing (D-75)
- [x] `PatientWriter.register(NewPatient)`: the name, the document, and a phone and an email each with
      its owner; `INVALID_DETAILS` for a contact that cannot be right; `PatientReader.relations()`
- [x] The booking screen takes the new patient's phone and email; the date of birth and the insurance
      are left to the patient's screen
- [x] The new patient's data never goes in the booking screen's address: it comes back through a flash
      attribute, on "Actualizar" and on every refusal
- [x] 5 integration tests, 2 of them rewritten from D-37's. 342 unit and 422 integration tests, 764 in
      total

**Verified with:** every new test failing when the code it covers was broken on purpose — 12
breakages: the use case in two transactions, the contact dropped by the service or its phone's owner,
the email dropped by the adapter, a wrong contact taken for wrong data, the new patient put back in the
address, not handed on or not shown, the phone's owner not sent, a phone typed alone not taken for a new
patient, and the contact's message. One more, a retry with an id already booked, was not caught by any
test: the booking screen, the use case's only caller, gives each attempt a new id, so the guard could
not be reached, and it was removed rather than tested (D-14). `mvnw verify`.

**Verified with the application running** on a throwaway PostgreSQL, with no new migration: in the
browser reception typed a boy with his mother's phone and email and a time already taken; the booking
was refused, the screen came back with everything typed and an address holding only the practitioner,
the day, the reason and the time, and nobody was registered. Another time booked him, with his phone
and email kept clean and his mother's; a three-digit phone came back as the contact's message, and
registered nobody.

---

## Explicitly postponed

These are not oversights: they are decided and waiting for their moment.

From v2, phase 3:

- Recording when the admins look at the readings of the clinical records, and for what (see D-71).

From v2, phase 1:

- Marking a check's result as reviewed, per practitioner (see D-56).
- Orders coming from elsewhere — on paper, or from another practice — and their validation, with
  the anti-corruption layer (see D-55).
- Files attached to the record as a whole, such as a report the patient brings (see D-57).
- Moving the attached files to an object store, when they outgrow the database (see D-57).

From v2, phase 2:

- A waiting room that calls patients by name or number, and a reason for "not seen" (see the context).

- Merging two records of the same patient, for someone registered under an alias who already had
  one (see D-60).

## Out of scope for v1

See `docs/context.md`, section "Planned evolution": arrival queue, attendance with more states,
reminders, extra patient data and web booking. The clinical record is v2's first phase, above.
