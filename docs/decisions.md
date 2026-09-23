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
**Date:** 2026-09-22 · **Status:** applied on 2026-09-23 (see D-29); time off still pending

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
| Storage, reads only | `Reader` | `AgendaReader` |
| Storage, writes only | `Writer` | `AppointmentWriter` |
| Storage, both | `Repository` | `AppointmentRepository` |
| Not storage | `Port` | `PractitionerSchedulePort` |
| Use case | `UseCase` | `BookAppointmentUseCase` |
| Persistence adapter | `Adapter` | `AppointmentJpaAdapter` |
| JPA row of a separated domain | `Row` | `AppointmentRow` |
| REST controller | `Controller` | `AppointmentController` |
| Use case input | `Command`, nested in its use case | `BookAppointmentUseCase.Command` |
| Request/response payload | `Request` / `Response` | `BookAppointmentRequest` |

Entities and value objects carry no suffix on purpose: they are the vocabulary of the
business, and `AppointmentEntity` says nothing that `Appointment` does not.

Renamed when this was agreed: `Bookings` → `BookingService`, `BookingRuleViolation` →
`BookingRuleViolationException`, and the two collision exceptions likewise. `BookingPolicy`
kept its own suffix, since `Policy` already reads as what it is and `BookingPolicyService`
would only add noise.

**Storage is named by what it does with the data, not by its architectural role.** `Port`
states the role; the other three state the shape of the access, which is the useful
information: `Reader` when it only reads, `Writer` when it only writes, `Repository` when it
does both. `Port` is left for dependencies that are not storage at all, such as the
practitioner's schedule or sending a notification.

The suffix therefore says whether a dependency can modify anything, which is visible at the
field declaration without opening the interface. `AppointmentRepository` both reads and
writes, hence its name; the Spring Data interface that will back it is a different file with
a different name, so the two do not collide.

**A `Command` used by a single use case is declared inside it**, as a nested record. The
one-to-one relationship is then expressed by the language instead of by a naming convention,
and the folder holds fewer files. A use case whose input is a single value takes it directly:
`CancelAppointmentUseCase.handle(AppointmentId)` needs no wrapper.

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

---

## D-20 — Use cases are plain objects until there is an adapter
**Date:** 2026-09-22 · **Status:** applied; the annotations arrived with the adapter

The use cases carry no `@Service` and no `@Transactional`. Nothing implements
`AppointmentPort` yet, so annotating them would either fail the context at startup or force
a stub adapter into existence — scaffolding by another name (D-14). They are constructed by
hand in their tests, and the annotations plus the transaction boundary arrive in the same
stage as the persistence adapter.

Two things settled while writing them:

- **`AppointmentPort` reads per day**, not per exact range. A day holds a handful of
  appointments, so fetching it whole and letting the domain do the precise overlap check
  keeps the query trivial and portable: nothing has to compare against a stored end time.
  The same daily reads are what the agenda view will need.
- **Booking is idempotent, rescheduling is not.** An id that already exists means the
  request already went through, so booking returns the existing appointment instead of
  creating a second one. Rescheduling cannot work that way: asking twice for the same move
  is indistinguishable from asking again, and the second attempt simply finds the
  appointment already where it should be.

`CancelAppointmentUseCase` deliberately does not go through `BookingService` or read the
agenda: cancelling frees a slot instead of taking one, so there is nothing to check against,
and the aggregate refuses a second cancellation by itself.

---

## D-21 — One transaction per use case, opened by the use case
**Date:** 2026-09-22 · **Status:** accepted

`@Transactional` sits on the use case, not on the adapter. Reading the agenda, deciding and
storing have to be one step: an adapter that opened a transaction per call would let the
read commit before the write, and the decision would rest on data that is no longer current.

The cost is that the application layer now imports `org.springframework.transaction`. That
is accepted: the transaction boundary is a decision about a business operation, which is what
this layer is for. The domain stays clean, and `BookingService` is declared as a bean in
`AppointmentConfiguration` rather than annotated, so the domain keeps no reference to Spring.

**A limit worth stating plainly.** The transaction does not make the overlap check
concurrency-safe. Under the default isolation, two receptionists booking the same slot at the
same time can both read a free agenda and both insert. Making it airtight needs either a
PostgreSQL exclusion constraint over a time range — which H2 cannot express — or a pessimistic
lock on the practitioner's day. So D-09's promise of a database-level overlap check is
**postponed to the PostgreSQL move**, and until then the guarantee is: one receptionist at a
time is safe, two racing on the same slot are not. With three or four practitioners and a
single front desk, that is a risk worth naming rather than paying for now.

## D-22 — Ids are uuid for every aggregate
**Date:** 2026-09-22 · **Status:** accepted

`PatientId` and `PractitionerId` wrap a `UUID` like `AppointmentId` does (D-19), instead of a
`Long`. The `appointment` table needed the final column type, and having the reference
columns disagree with the tables they will point at would have meant migrating them later.

---

## D-23 — Business operations are POST on a sub-path; each module translates its own errors
**Date:** 2026-09-22 · **Status:** accepted

**Routes.** Cancelling is `POST /api/appointments/{id}/cancel` and rescheduling is
`POST /api/appointments/{id}/reschedule`, rather than `DELETE` and `PATCH`. They are business
operations with rules of their own, not edits to a field, and nothing is ever deleted — a
cancelled appointment is kept as a record, because the patient file will show it.

**Booking answers 201 even when the id was already known.** The outcome the client asked for
is the same either way, and the body says which appointment it ended up with. Distinguishing
"created" from "already there" would mean the use case reporting which of the two happened,
for a difference no client acts on.

**`id` in the request body is optional.** A client that wants a retry to be safe generates
one and reuses it; one that does not care leaves it out and the server generates it (D-19).

**Errors are translated per module.** `ErrorResponse` lives in `shared/web` because the shape
of an error is a property of the API as a whole — one thing for a client to parse. The
translation, however, is scoped to the module with
`@RestControllerAdvice(assignableTypes = AppointmentController.class)`, so no shared class has
to import this module's exceptions (D-05). A booking rule that says no is a 409: the request
was well formed and the client can retry with another time.

When a second module gets endpoints, what moves to a shared advice is the validation
handling; the rules stay in their module.

**Responses carry plain values, not domain types.** `AppointmentResponse` exposes `UUID`s and
not the id wrappers, so the JSON does not grow a nested object per id and the contract does
not move when the domain does. It also carries `endsAt`, which the domain derives, because
every client would otherwise compute it.

An absent note comes back as an explicit `null` instead of being left out, so a client knows
the field exists.

---

## D-24 — Three boundaries, three mechanisms
**Date:** 2026-09-22 · **Status:** accepted

Business rules are reachable through one entrance and cannot be bypassed. What enforces that
depends on which boundary is being crossed:

| Boundary | Mechanism |
|---|---|
| Inside a module, towards the domain | package visibility, plus one concrete domain service as the way in |
| Between modules | an interface in the module's `api/`, with its own DTOs |
| Towards infrastructure | ports: `Reader` / `Writer` / `Repository`, or `Port` when it is not storage |

**Inside a module the entrance is a concrete class, not an interface.** What makes the rules
impossible to skip in `appointment` is that `Appointment.book` and `movedTo` are
package-private — not that `BookingService` is an interface, which it is not. An interface
with a single implementation would leave `BookingServiceImpl` just as public while adding a
file to jump through; `Impl` as a suffix is the usual sign of an interface that exists because
interfaces are expected. An interface earns its place when there is more than one real
implementation, or when the consumer must not see the concrete type — which is exactly the
case between modules.

**A port is an outbound dependency**, what the module needs from the outside. It is not the
entrance to the domain; using it as one inverts the idea.

Consequence for the modules still to come: `AvailabilityPolicy` in `practitioner` will be a
package-private concrete class reached only through the module's service, while what
`appointment` sees is the interface in `practitioner/api/`.

---

## D-25 — Layered inside `patient` and `practitioner`, with the rules kept pure
**Date:** 2026-09-22 · **Status:** accepted

Only `appointment` separates the domain from persistence (D-16). The other two map JPA
directly onto their classes, which is what D-05 allows and what keeps them short.

- **`patient` is layered, plainly.** Full name, optional national ID, search. No invariant an
  `@Entity` cannot hold, no rule needing context. A `Row` plus a translation, a port and a use
  case per operation would be ceremony.
- **`practitioner` is layered with the rules pulled out.** The practitioner, the reasons and
  the weekly hours are CRUD. "Is this practitioner free between 10:15 and 10:45?" is not: it
  crosses the day's hours, subtracts breaks and subtracts time off. That calculation lives in
  plain classes with no JPA and no Spring — `TimeRange`, `WeeklyHours`, `AvailabilityPolicy` —
  because the tests that matter there are boundary cases, and running them through HTTP or a
  database is slow and opaque for what is arithmetic over hours.

**Each module keeps everything package-private except its `api/`.** `patient` is therefore a
flat package: with the controller and the service in the same package as the entity, the
service need not be public, so nothing outside the module can reach it. The suffixes say what
each file is (D-18).

---

## D-26 — A patient identifies with a typed document, not with a bare number
**Date:** 2026-09-22 · **Status:** accepted

`IdentityDocument` is a value object of two parts: a `DocumentType` — `DNI`, `LC`, `LE`,
`CI`, `PASSPORT` — and the number. It replaces the plain `nationalId` string.

**Why the type is not optional decoration.** DNI 30111222 and passport 30111222 are two
different people. With a bare number, the unique constraint refuses the second patient, and a
search by number mixes them up. The constraint now covers the pair.

`LC` and `LE` are the older booklets, still the only document some elderly patients carry, and
`PASSPORT` covers foreign patients. Keeping the Argentine names is deliberate: it is what
reception reads off the document in their hand.

**It normalizes what reception types.** Dots, spaces and dashes are removed and letters are
upper-cased, so "30.111.222" and "30111222" are the same document. Without that the duplicate
check would not see them — pinned down by a test.

**Either both parts or neither.** A number without a type identifies nobody, so half a
document is a 400 rather than something silently stored. Absent altogether stays the ordinary
case of a patient with no document.

**A DNI is only digits.** The only per-type rule so far, and the one that keeps obvious
rubbish out. No length check: old documents have fewer digits and rejecting them would be
wrong.

This also prepares what the context already anticipates: web booking will need "a national ID
or another form of identification", which a single `nationalId` column could not express.

*Note:* `V2__patient.sql` was rewritten in place rather than followed by an `ALTER`. The
database is in-memory H2 and nothing is deployed, so there is no migration to preserve; from
the first real deployment onwards, migrations are append-only.

The full name stays a single field. Splitting it into given and family names is a later
decision and nothing in v1 needs the parts.

---

## D-27 — Transactions per method, and every write saves explicitly
**Date:** 2026-09-23 · **Status:** accepted

Two rules for every service and use case:

- **`@Transactional` goes on each method, never on the class.** Writes carry
  `@Transactional`, reads carry `@Transactional(readOnly = true)`. A class-level annotation
  overridden by some methods reads as inconsistent — methods that write show nothing, methods
  that read show something — and understanding it requires knowing the inheritance rule. With
  one annotation per method, each method says what it does on its own.
- **Every write calls `save()`, even when Hibernate would persist the change by itself.** In a
  transaction, Hibernate writes any modified entity on commit (dirty checking), so a method that
  loads an entity and changes it works without `save()`. That is the implicit saving D-16 named
  as a risk: whoever reads the method sees no write and may think the change is lost, or add a
  "missing" call that changes nothing. The explicit call costs nothing and makes the write
  visible.

`readOnly = true` is not decoration: Hibernate skips checking loaded entities for changes on
commit, which on a search returning many rows is work avoided, and it states that the method
modifies nothing.

This came out of `PatientService`, which had `@Transactional` on the class, `readOnly`
overrides on the reads, and an `update` that relied on dirty checking. `appointment` already
followed both rules.

---

## D-28 — How the practitioner module models reasons and the week
**Date:** 2026-09-23 · **Status:** accepted

**A break is the gap between two stretches of the same day.** Monday 09:00-13:00 plus
14:00-18:00 is a working day with a lunch break. There is no separate table or concept for
breaks: one concept instead of two, and "is this slot inside the week" reduces to "does it fit
inside one stretch". A consequence is that stretches which meet (09:00-13:00 and 13:00-18:00)
are rejected rather than accepted: they describe a day with no break, which has to be written
as one stretch, or the gap that means "break" would be ambiguous.

**Reasons have their own id and are added or changed one at a time.** A reason can be renamed
without losing its identity. The week, instead, is replaced whole, because it is edited as a
table. There is no delete for a practitioner or a reason: nothing in v1 asks for it and both
are referenced by past appointments.

**Changing a reason's duration does not move booked appointments.** They keep the duration
they were booked with; the agenda does not change under reception's feet.

**"Consulta" is not enforced.** The context describes each practitioner as having
"consulta" plus two or three more, but as a description of the practice, not as a rule the
system should police. Reception adds whichever reasons apply.

**Both collections of the practitioner are fetched eagerly.** They are tiny and every use of a
practitioner needs them, so lazy loading would only add the risk of reading them after the
transaction closed (`open-in-view` is off). The working hours are a `Set`, because Hibernate
cannot eagerly fetch two `List`s at once, and a duplicate stretch is rejected before it gets
there anyway.

**Refinements to D-25:**

- *Value objects may carry mapping annotations.* `WorkingHours`, like `IdentityDocument`, is an
  `@Embeddable` record. The annotations let JPA store it inside its entity; nothing in it needs
  JPA to be built or tested. What D-25 meant by "no JPA" is exactly that, and it holds.
- *`TimeRange` was not needed.* `WorkingHours` is already the range, with its day.
- *`WeeklySchedule` only validates the week for now.* Answering whether a slot falls inside it
  arrives with the code that asks — the booking check in `appointment` — and
  `AvailabilityPolicy` arrives with time off, when there is more than one thing to combine
  (D-14).

**The validation handler moved to `shared/web`.** The first two modules had identical copies,
kept on purpose until a third one confirmed every module wants the same shape. It now lives in
one global `ValidationExceptionHandler`, which imports nothing from any module, so sharing it
couples nothing (D-23). The module advices keep only their own exceptions.

---

## D-29 — How `appointment` asks `practitioner`
**Date:** 2026-09-23 · **Status:** accepted

The connection follows D-24 at both ends:

- **`practitioner` exposes an interface in its `api/`:** `PractitionerReader`, with
  `findReason` and `worksDuring`, answering with its own `ReasonDetails`. `PractitionerService`
  implements it; the class stays package-private, so the two interface methods are the only
  public way into the module.
- **`appointment` asks through its own port:** `PractitionerSchedulePort`, declared in the
  application layer in this module's terms — `PractitionerId`, `TimeSlot`. The use cases depend
  on that port, not on the other module's api. An adapter in `appointment/external/` translates
  between the two, and it is the only class in `appointment` that imports anything from
  `practitioner` — only from `api/`, verified with a grep.

The port is a `Port` and not a `Reader` because it is another module, not storage (D-18).
`external/` holds the adapters towards other modules, as `persistence/` holds the one towards
the database.

**What changed in booking:**

- **The duration is no longer sent; it follows from the reason.** The request carries a
  `reasonId`, the use case asks for that reason's duration with this practitioner, and builds
  the slot from it. The domain still only sees a start and a duration.
- **A slot must fit inside one stretch of the practitioner's week**, or it is refused with a
  409, like an overlap: the request was fine, the time is not available. A slot that starts in
  the morning and ends in the lunch break does not fit, even though its start is working time.
- **A reason of another practitioner is unknown here**, and so is a reason that does not exist.
  Both are a 400: the request names something that cannot be booked.
- **Rescheduling moves the start only and keeps the booked duration.** An appointment records
  what was booked, and changing a reason's duration does not move the agenda (D-28). The new
  start is checked against the week and the agenda alike.

`OutsideWorkingHoursException` is raised by the use case, not the domain, because the domain
does not know the week; it extends `BookingRuleViolationException` so it is answered like any
other booking rule.

**The open question in D-15 is settled: the appointment keeps the reason as text.** It is a
snapshot of what was booked, together with the duration. Renaming a reason later does not
rewrite past appointments, which is what the patient file should show.

**Tests of `appointment` now book with real practitioners.** The use-case tests use an
in-memory `FakePractitionerSchedule`, where a practitioner works at any time unless a test
narrows it. The integration tests create practitioners through the practitioner module's
public endpoints with a `PractitionerFixture`, staying out of its internals the same way the
code does.
