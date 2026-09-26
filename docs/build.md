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

### Phase 2 — The arrival queue `[~]`

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

---

## Explicitly postponed

These are not oversights: they are decided and waiting for their moment.

From v2, phase 1:

- Marking a check's result as reviewed, per practitioner (see D-56).
- Orders coming from elsewhere — on paper, or from another practice — and their validation, with
  the anti-corruption layer (see D-55).
- Files attached to the record as a whole, such as a report the patient brings (see D-57).
- Moving the attached files to an object store, when they outgrow the database (see D-57).

From v2, phase 2:

- Reading the read log of the clinical records, by the admins (see D-59).
- Recording refused attempts to read a record, with the security stage (see D-59).
- Merging two records of the same patient, for someone registered under an alias who already had
  one (see D-60).

From v1:

- A user administration screen (see D-04); it has a module to land in since D-43.
- Registering a new patient and booking them in **one transaction** from the booking screen;
  today they are two, and a refused booking leaves the patient registered (see D-37).

## Out of scope for v1

See `docs/context.md`, section "Planned evolution": arrival queue, attendance with more states,
reminders, extra patient data and web booking. The clinical record is v2's first phase, above.
