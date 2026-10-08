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

### Phase 4 — The patient's extra data `[x]`

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

**Phase 4 of v2, the patient's extra data, is closed.** Reception records a patient's date of birth, a
phone and an email — each saying whether it is the patient's own or a responsible person's, since a
child has no phone and an adolescent may share their mother's email — and their health insurance, with
a history of every change; nothing of it is required, a minor's responsible person included.
Practitioners read the age and the insurance beside the clinical record, never how to reach the
patient, and the admins read none of it. A new patient booked from the booking screen is registered
and booked in one transaction, with their contact, and what is typed of them stays out of the
screen's address. What it left for later — consent to be contacted, the confidentiality of adolescents
in the reminders, the cash desk, and the email of a web account — is in the context's planned
evolution.

### Phase 5 — Reminders and the cancellation log `[x]`

Scope in `docs/context.md`, section "v2, phase 5 — reminders and the cancellation log". Delivered in
parts; each one is usable on its own. The technical design of each part is agreed before it is coded.

#### Part 1 — The cancellation log `[x]`
- [x] `docs/context.md`: the scope of phase 5
- [x] `Cancellation`: when, who, how — `BY_HAND` or `TIME_OFF` —, who asked — `PATIENT` or
      `PRACTICE` — and a note of up to 200 characters; a time off is always asked by the practice and
      recorded under whoever recorded it; kept by the appointment, `Appointment.cancelled(Cancellation)`
      (D-76)
- [x] `CurrentUserPort`, answered by `CurrentUserReader`, to say who cancelled
- [x] `V31`: five columns on `appointment`, all or nothing and only on a cancelled one, and a time off
      asked by the practice; an index by when it was cancelled
- [x] Appointments cancelled before this phase keep nothing: "Sin registro de la cancelación"
- [x] `POST /api/appointments/{id}/cancel` takes `{requestedBy, note}`; every appointment answered
      carries `cancellation`; the agenda's "Cancelar" asks who asked, with no default, and a note
- [x] `ListCancellationsUseCase`: by the day of the cancellation and by practitioner, the last week by
      default, at most 500
- [x] Reception: `GET /api/cancellations` and `/cancellations`, whole; the patient's appointments say who
      cancelled, when, how and who asked, and the note
- [x] The admins: `GET /api/cancellation-log` and `/cancellation-log`, with their header, without the
      patient and the note; practitioners reach neither
- [x] 11 unit and 26 integration tests. 353 unit and 448 integration tests, 801 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 48
breakages in all, none surviving and all compiling: the time off asked by anyone, the note untrimmed,
kept blank or one character longer, who or how not required, the appointment keeping no cancellation
or one on any state, the use cases recording another user, another time, ignoring who asked or the
note, the list's default days, reversed days, its limit and its last day, the row losing the note or the
cancellation, the oldest first, any practitioner, the end of the range included, no limit, both checks
of V31 dropped, who asked not required by the API, the answers without the cancellation or with the
wrong time, the admins losing their route or their screen, the agenda cancelling without who asked or
dropping the note, the screens' words for an old cancellation, a time off and who asked, the admins'
header, the patient and the note missing from reception's list, and each header's link; `mvnw clean
verify` with the PostgreSQL tests running V31.

**Verified with the application running** on a throwaway PostgreSQL: taken to V103 by the previous
commit's build, with an appointment cancelled by hand, one cancelled by a time off and one booked, and
started: V31 applied out of order and left the three as they were. The two old cancellations said
"Sin registro de la cancelación". Through the API, a cancellation with no body or no `requestedBy` was a
400, and one asked by the patient with a note recorded `recepcion`, the time and the note; a time off
recorded `TIME_OFF` and `PRACTICE`. Reception's list showed the patient and the note, the admins' list
neither, and each was a 403 to the other role and to a practitioner. In a browser, "Cancelar" without
who asked was stopped by the screen, and with "el consultorio" and an accented note it cancelled and
the patient's screen said so.

#### Part 2 — Consent to be contacted `[x]`
- [x] `Consent`: `PATIENT` or `OWNER` — the responsible person a channel belongs to —, one for the phone
      and one for the email; the owner's only on a responsible person's channel, and none without the
      channel (D-77)
- [x] `Patient.changeConsent`, apart from the details; `changeDetails` keeps a channel's consent only
      while its number, address and owner stay as they were
- [x] `V32`: `phone_consent` and `email_consent`, checked against their channel; the history's field
      check widened
- [x] Every consent given, changed, withdrawn or dropped in the history of the details, as
      `PHONE_CONSENT` or `EMAIL_CONSENT`
- [x] `PUT /api/patients/{id}/contact-consent`, `phone.consent` and `email.consent` in every patient
      answered; "Recordatorios" on the patient's screen, offering the owner's consent only for a
      responsible person's channel
- [x] Reception alone: practitioners never see it, the admins reach none of it; the patients on the
      books start without it
- [x] 9 unit and 10 integration tests. 362 unit and 458 integration tests, 820 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 21 breakages,
none surviving and all compiling: a new phone or email keeping its consent, an unchanged one losing it,
a consent without its channel, the owner's on the patient's own channel, the history without the phone's
consent, without who gave it or what it was, the service keeping no history, the API swapping the
channels or answering without the consent, the three checks of V32, the screen never offering the owner
or offering it on the patient's own phone, not showing what was chosen, the history's words, a wrong
consent breaking the screen, and the patient with no channel told nothing; `mvnw clean verify` with the
PostgreSQL tests running V32.

**Verified with the application running** on a throwaway PostgreSQL: taken to V103 by the previous
commit's build, with a boy who had his own phone and his mother's email, and started: V32 applied out of
order and both channels came back with no consent. The owner's consent on his own phone was a 400; his
own for the phone and his mother's for the email were kept, and a new phone number dropped the first and
not the second, the history saying each step. The admin and a practitioner were refused the route. In a
browser, a patient with only her own email was offered her consent alone, and saving it showed it chosen
and in the history.

#### Part 3 — The reminders by email `[x]`
- [x] A module of its own, `reminder`, laid out as a hexagon; `ArchitectureTest` checks its boundary
      like the rest's (D-78)
- [x] `ReminderPolicy`: no email, no consent, or a responsible person's email that only its owner agreed
      to for a patient of 13 or older on the day of the visit — or with no date of birth — is not sent;
      the patient's own agreement is enough on any of their channels
- [x] `SendRemindersUseCase`, run by `ReminderJob` once a day at `app.reminders.cron` (10:00): the
      appointments still booked from tomorrow to `app.reminders.days-ahead` days on (3); one sent or
      failed is not tried again for that time, one moved is reminded of its new time, one not sent is
      decided again the next day
- [x] `ReminderSenderPort`, answered by `EmailReminderAdapter` over `spring-boot-starter-mail`; Mailpit in
      `docker-compose.yml`; a sender that keeps the messages in the integration tests
- [x] The text, in Spanish: the practice (`app.practice.*`), a greeting, the day, the time, the
      practitioner, how to cancel; never the reason; a responsible person told whose appointment it is; a
      patient under an alias named nowhere and greeted "Hola"
- [x] `PatientReader.contactsOf` (`PatientContact`) and `AppointmentReader.findNotCancelledBetween`, the
      only things the module asks of others
- [x] `V33`: `reminder`, with a bcrypt hash of the address's SHA-256 and never the address or the text
- [x] `GET /api/reminders` and `/reminders`, for reception, by the appointment's day, a failed one marked
- [x] 24 unit and 9 integration tests. 386 unit and 467 integration tests, 853 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 41 breakages,
none surviving, among them every branch of the policy (no email, no consent, thirteen counted as a child or
from fourteen, no date of birth as a child, the patient's own consent not enough, the owner greeted as the
patient), a reason on any reminder, one not sent or a moved one settling the appointment, today's
appointments or a day too many, reminding again and again, a failure recorded as sent or stopping the run,
the age taken today, the practitioner left out, the address kept as its own hash or hashed bare, the list
in reverse, the checks of V33, the alias named, the consent or the owner's name lost on the way, the
answer without the reason, the list's default days, reversed days, the screen's words and header, and
each wording of the text and its escaping. Two first versions did not hold: the list's order, renamed in
the repository alone, did not compile, and was planted again in both places; and filtering out absent
appointments survived, since from tomorrow on none can be absent, so it was taken out (D-14) and the
api's method named for what it answers. The run was stopped once by the machine's lack of memory and
resumed where it was, the fault it left planted restored first. `mvnw clean verify` with the PostgreSQL
tests running V33.

**Verified with the application running** on a throwaway PostgreSQL and a throwaway Mailpit: taken to
V103 by the previous commit's build, with five appointments on the third day ahead and one on the fourth,
and started with the job set two minutes on: V33 applied out of order, and the run considered the five.
Mailpit got three — the adult on her own email, a child's on her mother's ("Estimado/a Laura Vivo: …
el turno de Lucia Vivo …"), and the patient under an alias ("Hola: … su turno …") —, with the accents
intact in text and HTML; the adolescent on his mother's email with her consent alone was not sent as
confidential, the patient without consent was not sent, and none had its address stored, only its hash.
With Mailpit stopped and the adolescent's own consent recorded, the next run tried him once and recorded
it as failed, sent nothing again, and decided the patient without consent anew. The reminders screen
showed all of it, the failure marked for a call.

**Phase 5 of v2, reminders and the cancellation log, is closed.** Every cancellation says who made it,
when, how — by hand or by a practitioner's time off — and who asked, with reception's note; reception
reads them whole and the admins without the patient and the note. Each of a patient's channels carries
its own consent, saying who gave it, and loses it when the number, the address or the owner changes. The
application reminds patients by email once a day of their appointments of the next days, on that consent,
never telling a parent of an adolescent's visit the adolescent did not agree to share, and records what
happened to each reminder — the address only as a hash — for reception to call whoever was not reached.
What it left for later is listed below.

### Phase 6 — Web booking for patients `[x]`

Scope in `docs/context.md`, section "v2, phase 6 — web booking for patients". Delivered in parts; each
one is usable on its own. The technical design of each part is agreed before it is coded.

#### Part 1 — The account `[x]`
- [x] `docs/context.md`: the scope of phase 6
- [x] A module of its own, `portal`, laid out as a hexagon; `ArchitectureTest` checks its boundary (D-79)
- [x] Reception opens an account for a verified patient of 13 or older — `AccountOpeningService`, one per
      patient —, with an email of the account's own; the patient activates it from a link to that email,
      valid for 72 hours and kept only as a SHA-256, and chooses their password (`PasswordPolicy`, moved to
      `shared`)
- [x] `AccountMailPort`, answered by `EmailAccountAdapter`; one sender for the practice, `app.mail.from`,
      replacing `app.reminders.from`; the links point at `app.portal.base-url`
- [x] A patient with an account keeps a verified identity: `Patient.changeIdentity` refuses a new name or
      document without the document seen (`VerificationRequiredException`, 409), asking `portal/api`
- [x] The patient logs in with their document — `DocumentLoginFilter` joins its type and number —, on two
      filter chains of their own, `/api/portal/**` and `/portal/**`, with a session key and a session registry
      of their own; the principal is named by the account's id, never the document (D-80)
- [x] `GET /api/portal/appointments` and `/portal/appointments`: their appointments ahead and of the last 12
      months, through `appointment/api/PatientAppointment`, which has no place for the note nor for who
      cancelled one
- [x] Reception's `/api/patient-accounts` and `/patient-accounts?patient=`, linked from the patient's screen:
      open, send the activation again, change the email, deactivate — ending its sessions at once — and
      reactivate; every action recorded in `patient_account_change`, by reception or by the patient
- [x] The admins read none of it
- [x] `V34`: `patient_account`, `patient_account_activation`, `patient_account_change`, with their checks;
      `V104`: Laura Medina, `DNI 27333444` / `paciente123`
- [x] 19 unit and 21 integration tests. 405 unit and 488 integration tests, 893 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 84 breakages, all
of them compiling, among them each rule of opening an account (a second one, an unverified identity, no
date of birth, thirteen counted as under or twelve allowed), the email's case, length and form, every state
an account goes through and its checks, who did each action, the token kept bare or expired a moment late,
the twelve months and both orders of the list, each part of the email's text and its link, the activation
not recorded, a failed sending said sent, the link not ended when used, replaced or deactivated, the
sessions not ended, a pending account's new email sent nothing and an active one's sent an activation, the
reader letting an inactive account log in or saying nobody or everybody has one, the reason, the state and
who asked lost on the way, the note's absence and `activationSent` where nothing was to go out, each
refusal's status, each word of the screens and their history, the document never joined or run together, a
patient read as someone else, the routes opened to any role or closed to the activation, one session key for
both sides, the patient module not asking for the account, nobody found by document, an account holder left
unverified, and the checks of V34. One of them first survived because the test read "el paciente" inside
"del paciente" elsewhere on the screen; the test now reads the history's cell. Another survives, and is
equivalent: without the guard of a username with no space, the lookup throws, and Spring Security turns that
into the same 401. The run was stopped once by the machine's lack of memory, during the 56th breakage; the
fault it left planted was restored, every file checked against its state before, and the rest run in three
shorter batches. `mvnw clean verify` with the PostgreSQL tests running V34.

**Verified with the application running** on a throwaway PostgreSQL and a throwaway Mailpit: taken to V103
by the previous commit's build with two patients — one verified and adult, one not — and two appointments,
one cancelled with a note, then started on this one: V34 applied out of order and V104 after it, the data
intact. Reception was refused an account for the unverified patient (409) and opened one for the other at
`Marta.Cuenta@Mail.com`, kept in lower case; Mailpit got the activation, accents intact, its link to
8081. The activation screen refused two different passwords, then activated the account; the patient logged
in typing `31.222.333` and read the booked appointment and the cancelled one "a tu pedido", without the
note. Reception's screen showed the account active and its history, the activation by "el paciente". The
development patient logged in as `DNI 27333444`. Renaming the patient without seeing the document was a
409, and a 200 seeing it; the admin and a practitioner were refused the accounts (403), and the patient the
staff's API (401). Deactivating ended a patient's open session at once — but not in a browser where
reception had logged in after the patient: one session registry for both kept one login per session. The
patients now have a registry of their own, and the same browser was sent to `/portal/login?expired`; this
case is checked live only, since MockMvc does not tell the registries that a session's id changed.

#### Part 2 — The patient's data `[x]`
- [x] From the account, the patient changes their contact and their health insurance with reception's rules,
      through `PatientWriter.changeContact`; `PatientReader.profileOf` answers what they read (D-81)
- [x] The consent of each channel: yes is theirs, no withdraws any, a channel left out stays; the screen
      offers the consent that stands first, "No" when nobody agreed
- [x] Each change in the history as made by the account, `portal:` and its id (`PatientUsername`), which the
      patient's screen at reception reads as "el paciente (web)"
- [x] The name, the document and the date of birth stay reception's
- [x] `GET /api/portal/profile`, `PUT /api/portal/profile/details` and `/contact-consent`, and *Mis datos*
- [x] 2 unit and 5 integration tests. 407 unit and 493 integration tests, 900 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 26 breakages, all
compiling: yes, no and nothing said on a channel each taken for another, the date of birth cleared, the
patient module's refusals not translated, each part of what the patient reads lost on the way — the consent,
the date of birth, the relation, the document, the plan —, the history naming the bare account, the
`portal:` name changed or never recognised, the email's owner taken from the phone's, the request's insurer
and the answer's owner lost, the email's consent sent as the phone's, the owner's consent not offered or "Sí"
offered first to whom nobody agreed, the screen's date of birth, and a refusal not caught on the screen. One
survived: the screen turned a blank relation into none, which the patient module already does, so it was
taken out (D-14). `mvnw clean verify`.

**Verified with the application running** on a throwaway PostgreSQL: the development patient, with an email
of her mother's that her mother agreed to, read *Mis datos* — her name, document and date of birth, and for
that email "Sí: lo autorizó Rosa Medina" first — and added a phone and her insurance; her phone offered "No"
first. She agreed on the phone and left the email's choice untouched: the phone's consent became hers and the
email's stayed her mother's, her date of birth unchanged. A phone of three digits was refused with what to
fix. Reception's screen of the patient listed the three changes by "el paciente (web)", and the history kept
`portal:` and the account's id, never her document.

#### Part 3 — Booking on the web `[x]`
- [x] Reception marks which reasons of each practitioner may be booked on the web — `reason.web_bookable`,
      false at first —, on the practitioners' screen and in the reason's `PUT` (D-82)
- [x] `WebBookingPolicy`: from tomorrow to `app.portal.booking-days-ahead` days (30), at the times the agenda
      offers, three appointments ahead at most per patient — every booked one counted, the limit binding the
      web only; the appointment module books it with reception's rules through `AppointmentWriter.bookOnWeb`
- [x] An email to the account that booked confirms it, without the reason; one that does not go out leaves
      the appointment booked, and the patient is told
- [x] `Appointment.bookedVia` (`RECEPTION` or `WEB`, `appointment.booked_via`); the agenda and the patient's
      appointments mark the web's "· Web", and the API answers `bookedVia`
- [x] `GET /api/portal/booking/options` and `/times`, `POST /api/portal/appointments`, and *Sacar turno*
- [x] `V35`: the reason's flag and the appointment's channel, the appointments already there booked at
      reception
- [x] 7 unit and 8 integration tests. 414 unit and 501 integration tests, 915 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 39 breakages, all
compiling and all caught: each rule of the policy (any reason, today, a day too many, any time, cancelled,
absent or past appointments counted, a fourth allowed), each part of the confirmation's text, the appointment
booked nowhere or always at reception, the channel not stored or read back as reception, the web's booking
made as reception's, a busy patient told the time is taken, no times offered, the agenda's mark, the API's
`bookedVia`, the reason's flag lost in the entity, the api, the answer, the API's request or the screen,
every reason or any day offered, the confirmation said sent or not wrongly or sent to someone else, the
policy skipped, a refusal not a 409, each refusal's words and the screen's last day, and V35's check. The run
was stopped twice by the machine's lack of memory; the fault left planted was restored, every file checked
against its state before, and the rest run in shorter batches. `mvnw clean verify` with the PostgreSQL tests
running V35.

**Verified with the application running** on a throwaway PostgreSQL and a throwaway Mailpit: taken to V104 by
the previous commit's build with two appointments reception booked for the development patient, then started
on this one: V35 applied out of order, the two appointments booked at reception and every reason closed to
the web. Reception ticked "Se reserva por la web" on Dra. Arias's "consulta"; the patient's *Sacar turno*
offered it alone, from tomorrow to 30 days on, and the times of a Wednesday from 09:00 to 17:30 without her
break. She booked 10:30: the agenda showed it "consulta · Web", stored as `WEB` without a note, and Mailpit got
the confirmation at her account's email — the day, the time and the practitioner, not the reason. A fourth
appointment ahead was refused with what to do instead.

#### Part 4 — Cancelling `[x]`
- [x] On the web, any of the patient's booked appointments — reception's too — up to
      `app.portal.cancel-hours-before` hours before it (24), `WebCancellationPolicy`; someone else's a 403
      (D-83)
- [x] From an opaque link in the reminder and in the booking's confirmation, without logging in: a
      `SecretToken` kept as its hash in `cancel_link` with the appointment and its time, good while the
      appointment is booked at that time and a day is left; none issued when there is not; the page shows when
      and with whom, never the patient
- [x] The reminder asks `portal/api/CancelLinkWriter` for the link; both emails say to cancel there and to
      call to change it
- [x] The cancellation log says the patient asked, and how — `ONLINE`, by the account, or `EMAIL_LINK`, by the
      link —; reception reads "el paciente (web)" or "el link del email", the admins "paciente (web)" alone, and
      their API no `cancelledBy` for them
- [x] `POST /api/portal/appointments/{id}/cancel`, `GET` and `POST /api/portal/cancel-links/{token}`, "Cancelar"
      on *Mis turnos* and `/portal/cancel`
- [x] `V36`: the two origins, asked by the patient, and `cancel_link`
- [x] 8 unit and 7 integration tests. 422 unit and 508 integration tests, 930 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 45 breakages, all
caught: each rule of until when and of what, the link for any appointment or time or kept bare, the origins
and who asked, the web cancelled by nobody, a moved appointment's link working, someone else's appointment
cancelled, the policy skipped, a link issued or read too late, a link's cancellation recorded as the web's,
the link's address, no link in the confirmation or the reminder, each wording of both emails and the link
not escaped, each word of reception's and the admins' lists and of the patient's appointments, the account
in the admins' API, a link leading nowhere a 409 and someone else's appointment not a 403, the screen's button
and refusals, the practitioner on the link's page, the routes needing a login, and both checks of V36. One
first did not compile and was planted again; one first survived because the test read "el link del email"
inside "Con el link del email", and the test now reads the cell. Telling the arrivals of a patient's
cancellation was taken out (D-14): a day ahead, nobody waits with it. A link cancelled at the very moment the
appointment is cancelled elsewhere is refused as not booked; that race is not tested. The run was stopped
twice by the machine's lack of memory; the fault left planted was restored, every file checked against its
state before, and the rest run in shorter batches. `mvnw clean verify` with the PostgreSQL tests running V36.

**Verified with the application running** on a throwaway PostgreSQL and a throwaway Mailpit: taken to V104 by
the previous commit's build, with the development patient's own email and her consent, and three appointments
reception booked — one then cancelled by hand, asked by the practice —, and started on this one with the
reminders' run two minutes on: V36 applied out of order, the cancellation by hand as it was. Mailpit got her
reminder of Monday with the link, in text and HTML; the link opened, with nobody logged in, a page with the day,
the time and Dra. Arias and nothing of her, cancelled it, and then said it no longer works. Logged in, *Mis
turnos* offered "Cancelar" on Tuesday's alone and cancelled it. Reception's list read "el paciente (web) · Por
la web" and "el link del email · Con el link del email", both asked by the patient, beside reception's own; the
admins' read "paciente (web)" for both; the rows kept `portal:` and the account's id, and `email-link`.

#### Fix — each one sees only their own header `[x]`
- [x] Found trying part 5a's screens: since part 1, every staff screen also showed the patients' header —
      "Mis turnos", "Sacar turno", "Mis datos" and their "Salir" —, since a fragment selector `header` also
      takes every `<header>` element, and `fragments.html` had two. Their routes ask for a patient, so it
      showed nothing of theirs
- [x] The staff's fragment is now `staffHeader`, on every staff screen
- [x] 1 integration test, failing with the old selector planted back on the agenda. 422 unit and 509
      integration tests, 931 in total

**Verified in a browser** against the application on a throwaway PostgreSQL: the agenda and the family groups
showed reception's header alone, and *Mis turnos* the patient's alone.

#### Part 5a — Reception forms the family group `[x]`
- [x] `FamilyGroup` in `portal`: a name and its members, each with whether they share their appointments and,
      for a child, who asked and what they are to them; a patient in several groups, once in each (D-84)
- [x] `FamilyGroupService`, the only way in: from 13 a patient joins by themselves — an adult sharing, an
      adolescent as they choose —; a child under 13 at the request of an adult of the group, with the relation
      and the proof seen; nobody without a date of birth; from 13 each member chooses whether to share
- [x] A child shares always, read from the date of birth, and turns private on their thirteenth birthday with
      nobody doing anything
- [x] Every change recorded, with who and when; removing a member leaves the rest, a parent's children too
- [x] `POST /api/family-groups`, `GET` them, by patient and by id, `…/members`, `…/children`,
      `…/members/{patientId}/remove` and `…/sharing`, `GET …/changes`; reception's alone
- [x] `/family-groups` and `/family-groups/{id}`, reached from the patient's screen: the members with their age
      and whether they share, who asked for a child, a search to add patients, sharing and removing, the history
- [x] `V37`: `family_group`, `family_member` and `family_group_change`
- [x] 13 unit and 7 integration tests. 435 unit and 516 integration tests, 951 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 79 breakages, all
caught: both ages' boundaries, each rule of joining, of adding a child — a child joining, an adolescent added
as one, twice, a requester out of the group or an adolescent, no relation, no proof, starting to share — of
removing and of sharing, a child's sharing read before and after 13 and without a date of birth, the name's
length and spaces, removing or sharing for everyone, the change's action, the group's and the member's and
the change's invariants, the history or the group not kept, each argument of the use cases mixed up, each
query and order of the storage, each field kept, each mapping and validation of the API, and each label,
refusal, message, button, link and search of the screens. One first survived — a change of the group's
creation about a member —, and a unit test now covers that invariant. Found on the way: the screen named a
member who had left as "un paciente que ya no está", since it read only the members' names; it reads now
everyone the history names. Taken out (D-14), since nothing reaches them: the fallbacks for a patient who is
not there (patients are never deleted), "el paciente (web)" in the history (nobody changes a group from the
web until part 5b), the name's `@NotBlank` and the relation's `@NotNull` (the group's rules answer both). The
history's order is tested by moving two entries' times, since the tests' clock stands still. The run was
stopped once by the machine's lack of memory; the fault left planted was restored, every file checked against
its state before, and the rest run in batches of seven. `mvnw clean verify` with the PostgreSQL tests running
V37.

**Verified with the application running** on a throwaway PostgreSQL and a throwaway Mailpit: taken to V104 by
the previous commit's build, with a family registered — Jorge, 48; Valentina, 15; Benjamín, 10; Nora, with no
date of birth —, and started on this one: V37 applied out of order. From Jorge's screen, *Grupos familiares*
led to "Familia Medina", created. Jorge joined sharing; Nora was refused for her date of birth, Benjamín for
being under 13; Valentina joined with her appointments private, and only Jorge was offered to ask for a child.
Benjamín was added at his request, "A pedido de Jorge Medina (padre)", "Siempre (menor de 13)". Valentina
started sharing; Jorge left, and Benjamín stayed. The history read the six changes, the most recent first,
naming Jorge after he left. Over the API, a second group with Laura took Benjamín as her child — refused
first without the proof — and he was in both. The practitioner, the admin and the waiting room were refused
the routes. Found on the way: every staff screen also showed the patients' header, since part 1 — the fix
above.

#### Part 5b — The members act for each other on the web `[x]`
- [x] `FamilyAccessPolicy`: from 18, a patient acts for the members of a group of theirs who share — a child
      always —, whether or not they share their own; under 18, or without a date of birth, only for
      themselves; worked out at each request, so a child's turn private on their thirteenth birthday (D-85)
- [x] *Mis turnos* and `GET /api/portal/appointments` show each member's appointments as the patient's own —
      those ahead and of the last year, with the reason, never the note —, with "Cancelar"
- [x] Booking for a member: "Para" on *Sacar turno*, `patientId` on the API; three ahead counted on the
      member's; the confirmation to the account that booked it, naming the member; someone else a 403
- [x] Cancelling a member's appointment as one's own, recorded as made by the account and asked by the
      patient; someone else's a 403
- [x] *Mi familia*, in the patients' header, and `GET /api/portal/family`: the groups, each member's name, age
      band and whether they share; the patient shares, stops sharing or leaves, recorded as theirs — "el
      paciente (web)" in reception's history
- [x] No migration
- [x] 6 unit and 7 integration tests. 441 unit and 523 integration tests, 964 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 63 breakages, all
caught: each condition of the policy — a teen acting, no date of birth, a group not theirs, themselves, the
sharing and the ages ignored —, the order and the fields of *Mi familia*, the members acted for unsorted or
anyone's, the patient left out of whom to book for, a member without a date of birth, the members'
appointments read as the patient's, cancelling only one's own, booking for oneself by id refused or always for
oneself, the confirmation and the answer naming nobody, the three counted on the booker, the email's words,
each route and mapping of the API, and each label, choice, refusal, message, button and link of the screens.
Before the run, the tests were made to tell the members' order from the order they joined in, to read a
member's past appointments, one without a date of birth, the patient's own refusal beside a member's, a member
with no appointments and a patient booking for nobody else. The run was stopped once and the full build once by
the machine's lack of memory; nothing had been left planted, and each was run again.

**Verified with the application running** on a throwaway PostgreSQL and a throwaway Mailpit, with the
development data: reception formed "Familia Medina" — Laura, the development patient with an account; Jorge;
Valentina, 15, private; Benjamín, 10, at Laura's request — and booked Monday for Benjamín and for Valentina.
Laura's *Mis turnos* showed Benjamín's and Jorge's appointments under hers, and nothing of Valentina's; *Sacar
turno* offered "Laura Medina (vos), Benjamín Medina, Jorge Medina", kept Benjamín through the times, and booked
Tuesday for him: "Turno reservado para Benjamín Medina". Mailpit got the confirmation at Laura's address:
"Hola, Laura Medina: El turno de Benjamín Medina quedó reservado…". She cancelled his Monday, read "Cancelado a
pedido del paciente", and a booking for Valentina forced from the page was refused with a 403. *Mi familia*
listed the four with their age bands and sharing; Laura stopped sharing and still saw Benjamín's and Jorge's,
then left, and Benjamín stayed in the group. Reception's history read both changes as "el paciente (web)".

#### Part 6 — Recovering the password, and locking the account `[x]`
- [x] Five failed logins in a row lock the account for 15 minutes, both settings; trying while locked neither
      counts nor lengthens it; a login that goes through starts the count again; the form's and the API's
      alike, through `PatientAuthenticationProvider`, which tells `portal/api/PatientLoginWriter`; the
      staff's users are not locked (D-86)
- [x] The login says the same of every failure, the lock included, and offers "¿Olvidaste tu contraseña?"
- [x] A link to the account's email, valid for an hour, to an account in use only and not twice within
      five minutes; asking says the same for any document; choosing the new password ends the link, lifts a
      lock and ends the account's open sessions
- [x] The patient changes their own password with the staff's rules, under *Contraseña*
- [x] The account's history records `RECOVERY_SENT`, `PASSWORD_RESET`, `PASSWORD_CHANGED` and `LOCKED`
      with nobody at reception; reception reads "pedido desde la web", "el paciente", or nothing for a lock
- [x] `POST /api/portal/password-recovery`, `/password-reset` and `/password`; `/portal/password-recovery`,
      `/portal/password-reset` and `/portal/password`
- [x] `V38`: the count and the lock on the account, `patient_account_password_reset`, and the history's new
      actions and who did them
- [x] 9 unit and 12 integration tests. 450 unit and 535 integration tests, 985 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 71 breakages, all
caught: each rule of the lock — never over, counted while locked, at six, a minute longer, the count or an old
lock kept, a success that clears nothing —, a reset that keeps the lock, a change that clears the count, each
state a password is recovered or changed in, a blank password, a count below none, a lock recorded by
reception, the link's times and hash, the lock or a failure or a success not kept, a link for any state or too
soon again, not kept, unrecorded or kept though it did not go out, reused, expired, of an inactive account or
always usable, the open sessions left, any current password, the same, any length, the change unrecorded, the
login never locked, the failures or successes untold, the routes needing a login, each route and mapping of
the API, each message, label and link of the screens and of reception's history, and both checks of V38.
Before the run, the tests were made to cover a link that did not go out, an account deactivated after asking,
V38's checks and the sessions ended by a reset. An unreachable check of the number of attempts was taken out
(D-14). The run was stopped once by the machine's lack of memory; the fault left planted was restored, every
file checked against its state before, and the rest run in batches of seven or fewer. `mvnw clean verify`
with the PostgreSQL tests running V38.

**Verified with the application running** on a throwaway PostgreSQL and a throwaway Mailpit: taken to V104 by
the previous commit's build, where three wrong passwords for the development patient counted nothing, and
started on this one: V38 applied out of order, her account with no failures. Laura logged in on the browser;
five wrong passwords over the API locked her account for 15 minutes, and the right one was refused too.
Asking twice for the link, from the browser, said the same both times and sent one email to her account's
address, valid for an hour. The link let her choose a new password, which logged in while the old one did not;
the link no longer worked; and the browser's open session was sent to the login, which said the session had
closed because the account was deactivated or its password changed. The history kept `LOCKED`,
`RECOVERY_SENT` and `PASSWORD_RESET`, with nobody at reception.

**Phase 6 of v2, web booking for patients, is closed.** Reception opens a web account for a verified
patient of 13 or older, who activates it from the email's link and logs in with their document, on a
login, a session and routes of their own. They read their appointments and change their own contact,
consent and health insurance; they book the reasons reception opens to the web, from tomorrow to 30 days on
and three ahead at most, and cancel up to a day before, as anyone does from the link in a reminder or a
confirmation, the cancellation log saying the patient asked and how. Reception forms family groups, and
from 18 a member sees, books and cancels the appointments of those who share theirs, a child's always, an
adolescent's only if they choose. Five failed logins lock an account for a while, a forgotten password is
chosen again from a link to the account's email, and every action on an account is in its history. What it
left for later is listed below.

### Phase 7 — The cash desk `[x]`

Scope in `docs/context.md`, section "v2, phase 7 — the cash desk". Delivered in parts; each one is
usable on its own. The technical design of each part is agreed before it is coded.

#### Part 1a — Charging privately in cash `[x]`
- [x] `docs/context.md`: the scope of phase 7
- [x] A new hexagonal module, `cashdesk`, with `api/VisitCashier`: the three ways into a queue call it in
      their own transaction, so a refused charge leaves no event, no visit and no nameless patient (D-87)
- [x] Reception charges a visit when it puts the patient into a queue, in the same step: the amount it
      types, in cash; without it the patient does not go in
- [x] The amount: pesos above zero, two decimals at most, up to `app.cash-desk.max-amount`, $ 99.999.999,99
      by default, refused with a 400 and its message, never cut by the database
- [x] An emergency goes in uncharged, its charge pending, recorded later or never — or paid as it comes in
- [x] A charge is never changed nor deleted, and records who and when; a visit is charged once, and two
      requests at once are refused by the unique key as one charged already
- [x] The day's cash desk: its movements and totals, by who recorded them, and the pending visits;
      reception reads it whole, the admins without the patients, the practitioners not at all
- [x] `GET /api/cash-desk`, `POST /api/visits/{id}/charge`, `GET /api/cash-desk-log`; `/cash-desk` and
      `/cash-desk-log`; an "Importe" field wherever reception brings a patient in, the agenda's "Llegó" included
- [x] `V39`: `cash_visit` and `cash_charge`, only ever inserted, with `ck_cash_charge_means` named for part 2
- [x] 26 unit and 18 integration tests, and `cashdesk` in `ArchitectureTest`. 477 unit and 553 integration
      tests, 1030 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 75 breakages, all
caught: each rule of the amount — above zero, two decimals, kept in cents, the ceiling ignored, exclusive or
not said —, each rule of the ceiling's setting, a visit started uncharged, charged twice, at the wrong time or
by someone else, a charge with nobody, the day's order, checks and totals, the ceiling skipped by either use
case, an emergency taken for none or the other way, the day asked ignored, a charge two requests at once let
through, the pending query and the day's range, what storage keeps and reads back, the reasons and the ceiling
handed to the queues, each status and field of the API, the admins' day, each message, amount, link and field
of the screens, the admins' routes, each of the three ways into a queue left uncharged or with the amount
dropped, each check, key and foreign key of V39, and the setting. Two gaps were found and closed first: the
patient's name in the API, and a charge's amount on reception's screen, which a total could hide. A control
that changed only a comment was not caught. The run was stopped once by the machine's lack of memory; the
fault left planted was restored, and the rest run in batches of four or fewer. `mvnw clean test` and
`mvnw verify`, and the seven `*PostgresIT` on PostgreSQL 18, which ran V39.

**Verified with the application running** on a throwaway PostgreSQL, taken from empty to V104: reception opened
Arias's queue and brought Ana in over the API without an amount, with zero and with one hundred million — each
refused with a 400 and its message, nobody in the queue — and then with $ 15.000,50; Juan came in as an
emergency without paying and was left pending. Charging him $ 5.000 took him off the pending list, and a second
charge was refused with a 409. Reception's `/cash-desk` showed both charges with their patients, the second as an
emergency, and $ 20.000,50 in all; the admin read the same charges and total without a name, over the API and on
`/cash-desk-log`, and was refused reception's; Arias was refused both. In the database the charges kept their
cents, and V39's checks, unique key and foreign key were there by name.

#### Part 1b — Refunds `[x]`
- [x] A refund is a movement of its own, up to what is left of its charge, with who, when and why — free text,
      required, up to 200 characters; never changed nor deleted (D-88)
- [x] A visit given back whole is charged again; a visit has its charges in order, and only the last may have
      something left
- [x] Two requests at once charging again or giving back are refused by the unique keys, so no more is ever
      given back than was charged
- [x] The day's cash desk subtracts it: each charge and refund recorded that day, and what was charged, given
      back and is left, overall and by who recorded them; a refund counts the day it is recorded
- [x] The admins read the refunds without why and without the patients; the practitioners, none of it
- [x] `POST /api/visits/{id}/refund`; "Devolver" and "Cobrar de nuevo" on `/cash-desk`; both days' answers say
      each movement's kind and the totals charged, refunded and left
- [x] `V40`: `cash_refund`, and `charge_no` with the unique key on the visit and the number
- [x] 5 unit and 9 integration tests more, and the 1a's rewritten for the new shape. 482 unit and 562
      integration tests, 1044 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 62 breakages, all
caught: each rule of a refund — why required, trimmed, at most 200, who recorded it, never more than what is
left, how much is left said —, what is left subtracting, a refund's time, a visit never charged again, a refund
added as a charge, a charge with something left before the last, the current charge ignoring what was given
back, each reason refused, the day's refunds and charges of other days, their order, the refunded total and
what is left, a refund's reason and who recorded it, each number and refusal of storage when two requests run
at once, the day found by its refunds, the order charges and refunds are read back, what a refund keeps, each
status, field and total of the API, each message, amount, link and total of the screens, and each key, check
and foreign key of V40, the old unique key kept included. A test's own check that could not tell a movement's
remainder from a total was replaced first, and a test was added for each user's totals on both screens. A
control that changed only a comment was not caught. The faults ran in batches of four to ten. `mvnw clean
test` and `mvnw verify`, with the seven `*PostgresIT` on PostgreSQL 18, which ran V40.

**Verified with the application running** on a throwaway PostgreSQL taken to V39 alone, with a charge of
$ 15.000,50 kept as part 1a kept it; started on this part, V40 made it its visit's first charge and left no
default behind. Over the API reception was refused a refund without why, one above what was left, and a second
charge while something was left; it gave the charge back in four refunds, the last "cobrado de más" sent in
UTF-8, was then refused a fifth, charged the visit again with $ 1.500 and was refused $ 1.500,01 of it. Arias
and the admin were refused the refund route. Reception's `/cash-desk` showed each refund as a row of its own
with its reason and a minus, and "Devolver (queda $ 1.500,00)" on the new charge alone; the admin read the
same refunds and totals over the API and on `/cash-desk-log`, without a reason or a name.

**Part 1 of phase 7 is done:** reception charges each visit privately in cash as the patient comes into a
queue, an emergency's later or never, and gives back what went wrong or what is owed, as movements of their
own that never change; the day's cash desk adds them up by who recorded them. Parts 2 and 3 follow.

#### Part 2 — The other means of payment `[x]`
- [x] Debit card, credit card, bank transfer and QR, besides cash: reception picks the means wherever it charges
      — as a patient comes in, as an emergency's pending visit is charged, and as a visit given back whole is
      charged again —; left out, it is cash, so what sent `amount` alone keeps working (D-89)
- [x] A refund goes out by its charge's means; no means asks for anything more — no instalments, no operation
      number
- [x] The day's cash desk splits its totals by means, overall and for each user, and says what should be in the
      drawer: what is left in cash alone
- [x] `cashdesk/api/Means` for the queues, turned into the domain's by `VisitCashierAdapter`
- [x] `means` beside `amount` in the three ways into a queue and `POST /api/visits/{id}/charge`; `byMeans` and
      `drawer` in `total` and each `byRecorder` of both days' answers; a "Medio de pago" beside every "Importe",
      cash chosen; a row for each means and "En el cajón" in the totals of `/cash-desk` and `/cash-desk-log`
- [x] `V41`: `ck_cash_charge_means` dropped and added again with the five means, in portable SQL
- [x] 4 unit and 6 integration tests more, and the part 1's given their means. 486 unit and 568 integration
      tests, 1054 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 47 breakages, all
caught: a charge, a visit arriving or charged again keeping cash whatever the means, the totals by means in
another order, a refund counted as a charge or not subtracted, the drawer counting every means, what was
charged, or the whole when there is no cash, cash taken by default for another means or the means ignored by
either use case, each means turned into another or dropped by the queues' adapter, the drawer and each means'
remainder of the API, a user without their means or with the total for a drawer, the means dropped by each
route of the API, by each of the three ways into a queue and by each of the five forms of the screens, each
means' word swapped, the screens' drawer, a means' row and the rows themselves, the means field left out of the
arrivals, the agenda and the cash desk, an option's word, the drawer's column and header, a user's means rows,
the admins' totals, and V41 forgetting QR or letting any means in. A control that changed only a comment was not
caught. The faults ran one at a time. `mvnw clean verify`, with the seven `*PostgresIT` on PostgreSQL 18, which
ran V41.

**Verified with the application running** on a throwaway PostgreSQL taken from empty to V104: over the API
reception was refused a means it does not know with a 400 and nobody in the queue, brought Ana in paying
$ 15.000 by debit and Juan $ 8.000 without saying the means, which was cash, and a nameless emergency in pending,
and charged it $ 5.000 by transfer. It gave back $ 5.000 of Ana's, which went out by debit, and $ 500 of Juan's,
in cash: the day said $ 22.500 left and $ 7.500 in the drawer, each means apart, for reception and the admin
alike; Arias was refused. On the screens reception brought in a nameless patient paying $ 1.200 by QR, and
charged by credit again a visit given back whole; `/cash-desk` and `/cash-desk-log` showed each means under the
user and the total, and "En el cajón", the admin's without a name. In the database each charge kept its means,
V41's check was there by name, and it refused a means it does not know.

**Part 2 of phase 7 is done:** reception charges each visit by the means the patient pays with, and the day's
cash desk separates them, counting only cash for what should be in the drawer. Part 3 follows.

#### Part 3 — The visits covered by a health insurance `[x]`
- [x] A visit covered by the patient's obra social or prepaid plan, only with an insurance recorded and a
      verified identity, keeping the insurance as it was that day (D-90): `InsuredPatientPort`, answered through
      `PatientReader`, so `patient/api` does not change
- [x] The token of that visit, optional: the insurer's check, outside the application, says whether it asks
      for one; trimmed, up to 50 characters
- [x] A copay charged by any means, or none, and given back as any charge; the day's movements and totals show
      the copays, not the visits that moved no money
- [x] An emergency's pending visit recorded later as covered or private
- [x] A visit marked covered by mistake: the coverage withdrawn, saying why, once its copay is given back whole,
      and the visit charged privately; reception's day lists the visits covered that day apart, as the pending ones
- [x] The visit held while it is charged, covered or withdrawn, since no unique key holds those together
- [x] `coverage: {token?}` in `POST /api/queues/{id}/session/patients` and `POST /api/appointment-arrivals`; `POST
      /api/visits/{id}/coverage` and `/coverage/withdrawal`; `copay` on each movement and `covered` in reception's
      day; "Con cobertura" and "Token" wherever reception brings a known patient in, the agenda's "Llegó"
      included; "Con cobertura" on a pending emergency, "Coseguro" and the list "Con cobertura" on `/cash-desk`
- [x] `V42`: `cash_coverage` and `cash_coverage_withdrawal`, each keyed by its visit and only ever inserted, and
      `copay` on `cash_charge`
- [x] 11 unit and 15 integration tests more, and the earlier ones given the new shape. 497 unit and 583
      integration tests, 1080 in total

**Verified with:** every new test failing when the code it covers was broken on purpose — 100 breakages, all
caught: who may go in covered — no insurance, an unverified identity —, each rule of the token — blank, trimmed, at
most 50 —, a covered visit with no copay refused, a copay not said to be one as it comes in, charged again or
covered later, a visit covered twice or covered once charged privately, a coverage withdrawn twice or with its
copay left, a withdrawn coverage still standing, a covered emergency still pending, a withdrawal without why or not
trimmed, the covered visits out of order or without a coverage, a movement never a copay, the coverage, the
token, the copay or its means dropped by each use case, the queues' adapter and each of the three ways in, the
patient taken as verified or insured, the member number for the plan, each row not kept, not read back, or taken
for a different one, the covered visits of the day and the pending query, the copay, the token and the member
number not kept, each status, field and list of the API, each message, label, link, list and field of the
screens, the agenda and the arrivals without coverage, a token typed alone or "Con cobertura" ignored, each of the
three use cases that write without holding the visit, and each key, foreign key and required column of V42. Five
gaps were found and closed first: the covered patients' names in the API, the cash desk's refusals of a coverage over
the API and on its screen, the copay's word on the screen, which a form's placeholder could hide, and the lock, by
three tests of two requests at once. An older test that read the day's first movement, which the tests' clock leaves
in no order, was made to read any, after it caught a breakage by chance. A control that changed only a comment was
not caught. The faults ran one at a time. `mvnw clean verify`, with the seven `*PostgresIT` on PostgreSQL 18, which
ran V42.

**Verified with the application running** on a throwaway PostgreSQL taken from empty to V104: over the API reception
was refused bringing Ana in covered without an insurance and then unverified, with nobody in the queue; once
verified, it was refused a token of 51 characters, and brought her in covered by Galeno, plan 220, with the token
" 123456 ", which moved no money; her insurance changed to OSDE afterwards, and the visit kept Galeno. Eva came in
covered by PAMI with a $ 2.000 copay by debit; Luis came in as an emergency, left pending, was covered later by
Swiss Medical with the token 777 and a $ 1.500 copay by QR, and was refused a second coverage with a 409.
Withdrawing Eva's coverage was refused while its copay was there and without why; once it was given back whole,
the coverage was withdrawn, refused a second time, and Eva charged $ 15.000 privately in cash: the day said
$ 16.500 left and $ 15.000 in the drawer, the copays marked, and listed the three covered visits, Eva's withdrawn.
Arias was refused the day and the coverage, and the admin's day said nothing of either. On the screens reception
brought Marta in with a token typed alone and a $ 1.200 copay by transfer, was refused Juan covered with "El
paciente no tiene obra social cargada: paga como particular.", and `/cash-desk` showed "Con cobertura" with each
insurance as it was, the token, "Coseguro" and "Quitar cobertura"; `/cash-desk-log` showed the copays' amounts
without a name or an insurance. In the database each coverage kept the day's insurance, each charge whether it was a
copay, and V42's keys were there by name.

**Part 3 of phase 7 is done:** a visit goes in covered by the patient's health insurance, with its token and its
copay, an emergency's pending visit is covered later, and a coverage marked by mistake is withdrawn as a fact of its
own; the day's cash desk counts the copays and lists the covered visits apart.

**Phase 7 of v2, the cash desk, is closed.** Reception charges each visit in the same step that puts the patient
into a queue, so nobody is seen unpaid by mistake, and an emergency goes in uncharged, its charge pending. A visit
is paid privately — in cash, by debit or credit card, transfer or QR — or covered by the patient's health
insurance, for a verified patient with one recorded, keeping the insurance as it was that day, the token when the
insurer's check asks for one, and a copay charged as any other. Nothing is changed nor deleted: a charge is given
back with a refund of its own and charged again, and a coverage marked by mistake is withdrawn, saying why. The
day's cash desk adds up what was charged, given back and is left, by means and by who recorded it, with what
should be in the drawer; reception reads it whole, the admins without the patients, the practitioners not at all.
The application records the money and issues no invoice. What it left for later is listed below.

---

## Explicitly postponed

These are not oversights: they are decided and waiting for their moment.

From v2, phase 7:

- Issuing invoices with ARCA, and printing receipts (see the context).
- Billing the insurers each month, with the discounts their audit may make (see the context).
- A price list, an opening float and counting the drawer at the close (see the context).
- Charging a visit the patient never came to (see the context).

From v2, phase 6:

- Registering a patient, or opening an account, from the web, and accounts for people who are not patients
  (see D-79).
- Moving an appointment on the web, booking today's appointments on the web, and confirming an appointment
  (see D-82, D-83).
- The patients reading what the law lets them ask for of their clinical record (see the context).
- Recording which account booked an appointment (see D-85).
- Telling the patient that the practice cancelled their appointment (see the context).
- Recovering a password by email and locking the account after failed logins for the staff's users, and
  two-factor authentication (see D-86).

From v2, phase 5:

- Reminders by WhatsApp or SMS, each one more adapter of the sending port, and marking a phone as
  WhatsApp (see D-78).
- Confirming an appointment from its reminder (see D-78); cancelling from it came with phase 6 (D-83).
- Telling the patient that their appointment was cancelled, and a history of the times an appointment was
  moved (see the context).
- A list of cancellation reasons, and practitioners reading the cancellations of their own appointments
  (see D-76).
- The practitioner's speciality and consulting room in the reminder, once the application knows them; a
  screen that checks whether a reminder went to a given address, against its hash; making up a day's
  run missed while the application was down (see D-78).

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
