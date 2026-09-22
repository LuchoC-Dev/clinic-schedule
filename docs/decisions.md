# Technical decisions

A log of decisions and the reasoning behind them. Each entry has a date, a status and the
rejected alternatives. A reverted decision is not deleted: it gets a `Superseded by` note.

---

## D-01 — Maven, Java 21, Spring Boot 3.5
**Date:** 2026-09-22 · **Status:** accepted

The most conventional stack in corporate Java environments, on a widely adopted LTS.

*Rejected:* Gradle (less common in the market this project targets); Java 25 (newer LTS,
adoption still low).

---

## D-02 — REST API as the main contract, minimal throwaway Thymeleaf views
**Date:** 2026-09-22 · **Status:** accepted

The future user interface will be two separate single-page applications, so the stable
contract is the REST API. The v1 Thymeleaf views exist only to see the application
working: agenda of the day, new appointment, confirm check-in.

**The design condition that keeps this separable:** views are thin and call the module's
application services. They never consume the REST controllers and hold no logic. When the
single-page applications arrive, `templates/` and the view controllers are deleted and
nothing else is touched.

*Rejected:* REST only (the application cannot be seen working); a single-page application
in v1 (too much work, outside the focus).

---

## D-03 — H2 in PostgreSQL mode, with no coupling to the engine
**Date:** 2026-09-22 · **Status:** accepted

We start on H2 so no infrastructure is required, but without tying ourselves to it:

- Standard JPA/Hibernate, no engine-specific functions.
- H2 in PostgreSQL compatibility mode.
- **Flyway** migrations in portable SQL from the first commit.

Moving to PostgreSQL must be a matter of changing the datasource and the dependency,
nothing else.

*Rejected:* `ddl-auto=update` (bad practice, and visible in a showcase repository);
PostgreSQL from the start (postponed, not rejected — see `docs/build.md`).

---

## D-04 — Spring Security with the RECEPTION and PRACTITIONER roles
**Date:** 2026-09-22 · **Status:** postponed to stage 8 (see D-14)

"Each practitioner sees only their own appointments" is a real authorization rule, not a
cosmetic one, so v1 implements it for real.

- Users live in the database, loaded by a migration (seed). No user administration screen
  in v1.
- User and practitioner are separate entities: a user with the `PRACTITIONER` role
  references a `practitionerId`.
- Role filtering lives in the service, not in the controller: reception may query any
  `practitionerId`; a practitioner is given their own and the received parameter is ignored.

*Rejected:* a simulated user passed as a header or parameter (leaves the central
requirement unimplemented).

**Postponed on 2026-09-22.** This was implemented in stage 1 and then removed: with no
patients, practitioners or appointments yet, authentication guarded nothing. It comes back
as stage 8, keeping the design above. Until then the application has no authentication, and
the agenda endpoints receive the `practitionerId` as a parameter with nobody checking who is
asking.

---

## D-05 — Feature modules, simple layers, boundaries by convention
**Date:** 2026-09-22 · **Status:** accepted

The structure is `patient/`, `practitioner/`, `appointment/`. Inside each module, simple
layers (web, application, domain, persistence). **Each module may organize its internals
differently**, according to what it needs.

Coupling rule: a module talks to another one only through that module's `api/` package
(interface plus its own DTOs). Everything else is package-private. Entities and
repositories of another module are never imported.

Boundaries are held **by convention**, without ArchUnit or Spring Modulith: fewer moving
parts for three modules. The accepted risk is that boundary erosion will not fail the
build automatically; it is watched during review.

*Rejected:* Spring Modulith (extra dependency and concepts); ArchUnit (postponed);
classic layers at the root (they fall apart once the patient file and the queue arrive).

---

## D-06 — Automatic cancellation via `@Scheduled`, with the state persisted
**Date:** 2026-09-22 · **Status:** accepted

A scheduled task sweeps overdue appointments every minute and cancels them, leaving the
real state in the database. The two deadlines are told apart by two fields of the
appointment: `checkedInAt` and `autoCancellationSuspended`.

- No check-in and not suspended: cancelled 30 minutes after the start time.
- No check-in and suspended: cancelled 24 hours after the start time.
- Confirmed check-in: never cancelled automatically.

*Rejected:* computing it lazily on read (the state is not persisted, which complicates the
history the patient file will show); a scheduler publishing a domain event (indirection
with no use today; to be revisited once reminders or attendance exist).

---

## D-07 — Appointment as a single aggregate, with state and check-in marks
**Date:** 2026-09-22 · **Status:** accepted

One `Appointment` aggregate with the state `BOOKED`/`CANCELLED` plus the two fields from
D-06. No separate table for check-ins or suspensions: v1 needs no further states, and the
attendance feature will replace them.

---

## D-08 — `appointment` reads duration and availability from the `api/` of `practitioner`
**Date:** 2026-09-22 · **Status:** accepted

The application layer of `appointment` asks the `practitioner` module two things:

1. The fixed duration of the reason for that practitioner.
2. Whether the requested time falls inside their availability (weekly schedule, breaks,
   time off).

The booking domain receives a **start time and a duration**, already resolved, and knows
nothing about what the reason means. `appointment` never sees `practitioner` entities, only
its DTOs.

---

## D-09 — Integrity rules in the database as well
**Date:** 2026-09-22 · **Status:** accepted

On top of the validation in the service:

- A partial unique index on the national ID, which still allows many patients without one.
- An overlap check inside the booking transaction.

With three or four practitioners, an elaborate locking scheme is not justified.

---

## D-10 — One fixed application time zone
**Date:** 2026-09-22 · **Status:** accepted

`America/Argentina/Buenos_Aires`, fixed, with `LocalDateTime` in the domain. No multi-zone
support. The clock is injected as a `Clock` so the cancellation deadlines can be tested.

The `Clock` bean and the time-zone property are introduced by the first stage that reads the
time, which is stage 4 (D-14).

---

## D-11 — Tests: unit tests for the domain plus key integration flows
**Date:** 2026-09-22 · **Status:** accepted

- Unit tests on the rules: overlap, duration lookup, cancellation deadlines.
- `@SpringBootTest` on the key flows: book, reschedule, cancel, confirm check-in, record
  time off.

*Rejected:* full per-layer coverage (a lot of code to maintain while the design still
moves); domain only (nothing would verify the Spring wiring or the database constraints).

---

## D-12 — Conventional commits through the `git-commit` skill
**Date:** 2026-09-22 · **Status:** accepted

The skill is copied into `.claude/skills/git-commit/` so the repository carries its own
commit convention.

---

## D-13 — English for the repository, Spanish for the screens
**Date:** 2026-09-22 · **Status:** accepted

Everything written in the repository is in **English**: packages, classes, methods,
variables, comments, table and column names, API error messages, test names, commit
messages and these documents.

The exception is the text the staff of the practice reads on screen, which stays in
**Spanish** — they work in Spanish. Today that is only `templates/login.html`.

The API returns messages in English; translating them for the user belongs to the
interface, which is where the future single-page applications will handle it.

Stage 1 was first written in Spanish and then translated wholesale. The translation also
renamed the base package to `com.clinic.schedule` and the user table to `app_user`, since
`user` is a reserved word in PostgreSQL and D-03 requires the schema to stay portable.

**Domain glossary:** patient, practitioner, appointment (`BOOKED`/`CANCELLED`), reason,
duration, weekly schedule, break, time off, check-in, reschedule, reception.

---

## D-14 — Only what the current stage uses
**Date:** 2026-09-22 · **Status:** accepted

Nothing is added before the stage that actually uses it — not code, not dependencies, not
configuration. No scaffolding for a future stage, however cheap it looks.

Stage 1 had been built with security, users, login, a uniform error contract, a `Clock`
bean, `@EnableScheduling`, and the thymeleaf and validation dependencies. None of it was
used yet, because there were no patients, practitioners or appointments. It was all removed
and stage 1 was reduced to an application that starts.

Each piece comes back where it is needed:

| Piece | Stage |
|---|---|
| `validation` dependency, error contract | 2, with the first endpoint |
| `Clock` bean and fixed time zone | 4, the first code that reads the time |
| `@EnableScheduling` | 5, with the auto-cancellation task |
| `thymeleaf` dependency and views | 7 |
| `security` dependency, users, login, roles | 8 |

**Why it is worth the rework:** a repository meant to show how an application grows must
show each piece appearing with its reason. Scaffolding with no caller cannot be reviewed,
cannot be tested, and ages into something nobody dares to delete.

---

## D-15 — Hexagonal inside the `appointment` module only
**Date:** 2026-09-22 · **Status:** accepted

`appointment` is organized as a pure domain surrounded by ports and adapters. The other
modules keep simple layers, which D-05 already allows.

The reason is not that the rules will change — if a rule changes, the domain changes and no
architecture prevents that. The reason is that `appointment` has two dependencies that do
not exist yet: the duration of a reason and the practitioner's availability. With those as
ports, the domain can be built and tested today, before the `practitioner` module exists,
and the agenda rules get unit tests that run without Spring or a database.

Consequences already visible:

- The domain has no framework or persistence annotations. The JPA adapter maps it.
- `patientId` and `practitionerId` are id wrappers, not `@ManyToOne` relations. Two bare
  `Long` parameters next to each other are the classic swapped-argument bug.
- Collision rules live in `BookingPolicy`, a domain service, because deciding them needs
  the rest of the agenda, which a single appointment cannot know. It receives the
  appointments to check instead of fetching them, which keeps it a pure function.
- Ports are not created until a use case calls them (D-14). The first stage is the domain
  alone.

**`restore` stays public — accepted risk.** The persistence adapter lives in another package
and Java cannot export a method to one package only, so the only ways to hide it would be to
put the adapter in the domain package or use JPMS. Both were considered and judged not worth
it. Using `restore` to bypass `BookingService` is therefore possible, but it takes writing
that call on purpose, the same way writing raw SQL against the table would; it is not a
mistake anyone falls into.

**No unvalidated way in.** `Appointment.restore`, the factory the persistence adapter uses
to read an appointment back, checks the same invariants as `book`. An aggregate protects
itself instead of relying on its callers having been careful, and a public static factory is
reachable by anyone, so "only the adapter should call this" is a comment, not a guarantee.

This is safe precisely because the rules that change over time — overlap, availability —
live outside the aggregate. What `Appointment` checks is structural (there is a patient, a
practitioner, a slot, a reason) and every stored appointment has always satisfied it, so a
future rule cannot make old rows unreadable.

**Open questions, to settle when `practitioner` exists:** the reason is carried as text, so
a renamed reason will not update past appointments; and whether that should become a
reference to the practitioner's reason list.

---

## D-16 — A pure domain only where there are rules to protect
**Date:** 2026-09-22 · **Status:** accepted

`appointment` keeps a framework-free domain with a separate JPA row class and a mapping.
`patient` and `practitioner` map JPA directly onto their classes.

Those two modules are data — a name, a national ID, a working schedule — with no invariants
to protect, so separating there would be ceremony. `appointment` is the one with rules, and
the separation buys three concrete things:

- **Final fields.** JPA needs a no-argument constructor and writes fields by reflection, so
  an `@Entity` cannot keep `patientId` and `practitionerId` final. Those finals are how the
  rule "an appointment never changes patient or practitioner" is enforced rather than
  remembered.
- **Explicit saving.** If the domain object were the entity, Hibernate dirty checking would
  persist any change made inside a transaction, with no `save()` call in sight. A method
  that mutates state to compute something intermediate would silently write it.
- **A schema that can move.** The table can store `duration_minutes` as an `int`, which is
  portable and easy to query, while the domain uses `Duration`, which is the right type to
  reason with.

**What the separation does not buy, contrary to the usual claim:** fast tests. An `@Entity`
is instantiated with `new` just the same; the domain tests are fast because they do not load
Spring, not because of this.

*Rejected:* annotating the domain of `appointment` as `@Entity` (fewer files, loses the
three points above); and `@Entity` on the domain **plus** a mapper, which pays both costs
and collects neither benefit.

---

## D-17 — Entity and domain services separated, with a single way in
**Date:** 2026-09-22 · **Status:** accepted

The pattern for every module that has rules, not just `appointment`:

- **The entity** guards what depends on itself alone, and no path can produce an invalid one.
  Every creation path validates the same invariants (see D-15).
- **Domain services** hold the rules that need context the entity cannot have, and they are
  the **only way in**. The mutating operations of the entity are restricted to the domain
  package, so the check cannot be skipped by accident.
- A service takes the context as a plain collection instead of reading it. Fetching data and
  the transaction belong to the use case, which keeps the domain free of ports.

In `appointment` that is `Bookings` (the way in) over `BookingPolicy` (the collision rules,
package-private) and `Appointment`. `Appointment.book` and `movedTo` are package-private;
only `restore` is public, because the persistence adapter lives in another package.

**The aggregate is immutable.** `cancelled()` and `movedTo()` return a new appointment
instead of modifying this one. The reason is concrete: `reschedule` has to validate the new
slot, and if it mutated first and checked afterwards, a rejected move would leave the
appointment carrying a slot nobody accepted. Returning a copy also lets every field stay
final. The use case stores what the service returns.

*Rejected:* putting the collision check inside the entity (it cannot know the rest of the
agenda); having the entity call a repository port (a factory doing I/O cannot be tested
without a double, and the transaction is not the entity's business); leaving `BookingPolicy`
public next to `Appointment.book` and trusting the use case to call both.

---

## D-18 — A file is placed by its folder or named by its suffix
**Date:** 2026-09-22 · **Status:** accepted

Either a folder says what a file is, or its name has to. When several kinds of type share a
folder — as they do in `domain/` — the name carries the whole weight, so each type ends with
the suffix of its kind:

| Kind | Suffix | Example |
|---|---|---|
| Entity / aggregate | none | `Appointment` |
| Value object | none | `TimeSlot`, `PatientId` |
| Enum | none | `AppointmentStatus` |
| Exception | `Exception` | `PractitionerAlreadyBookedException` |
| Domain service | `Service` | `BookingService` |
| Rule set reached through a service | `Policy` | `BookingPolicy` |
| Output port | `Port` | `AppointmentPort` |
| Use case | `UseCase` | `BookAppointmentUseCase` |
| Persistence adapter | `Adapter` | `AppointmentJpaAdapter` |
| JPA row of a separated domain | `Row` | `AppointmentRow` |
| REST controller | `Controller` | `AppointmentController` |
| Request/response payload | `Request` / `Response` | `BookAppointmentRequest` |

Entities and value objects carry no suffix on purpose: they are the vocabulary of the
business, and `AppointmentEntity` says nothing that `Appointment` does not.

Renamed when this was agreed: `Bookings` → `BookingService`, `BookingRuleViolation` →
`BookingRuleViolationException`, and the two collision exceptions likewise. `BookingPolicy`
kept its own suffix, since `Policy` already reads as what it is and `BookingPolicyService`
would only add noise.

---

## D-19 — Identity assigned on creation, as a UUID
**Date:** 2026-09-22 · **Status:** accepted

Ids are not generated by the database. Each aggregate gets its id when it is created, as a
value object wrapping a `java.util.UUID` — `AppointmentId` for appointments.

**What it buys:**

- **Idempotency.** Reception double-clicks, two requests arrive with the same id, and the
  second one does not create a second appointment. At a front desk this is a real problem,
  not a theoretical one.
- **No `null` id.** One less state to reason about, and identity comparison always works —
  it used to need a special case for appointments not stored yet.
- The id is known without a round trip to the database.

**What it costs:**

- **Unreadable ids.** "Appointment `9f8a…`" cannot be read out over the phone the way "1234"
  can, and it is noisier in URLs and logs.
- 16 bytes instead of 8, and a random v4 fragments the index. At this scale — three
  practitioners, a few thousand rows a year — neither matters. A time-ordered v7 would avoid
  the fragmentation, but Java 21 does not generate one natively and it is not worth a
  dependency or hand-written generation here.

Columns will be `uuid`, which both H2 and PostgreSQL support natively (D-03).

**Note on `restore`:** assigning ids on creation removes the side effect that `restore` used
to have of being unusable for new appointments, since it required an id nobody had yet. That
protection is gone, which was weighed and accepted when `restore` was left public (D-15).
