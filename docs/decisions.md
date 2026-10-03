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
**Date:** 2026-09-22 · **Status:** applied on 2026-09-23 (see D-33)

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
**Date:** 2026-09-22 · **Status:** applied on 2026-09-24: the application runs on PostgreSQL, H2 stays for the integration tests (D-44)

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
**Date:** 2026-09-22 · **Status:** applied on 2026-09-23 (see D-34), without the foreign key (D-30);
"the received parameter is ignored" superseded by D-39

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
**Date:** 2026-09-22 · **Status:** accepted; the boundaries checked by ArchUnit since D-42

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
**Date:** 2026-09-22 · **Status:** applied on 2026-09-23 (see D-31); the cancellation became an absence in D-61

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
**Date:** 2026-09-22 · **Status:** applied on 2026-09-23 (see D-29, D-32)

The application layer of `appointment` asks the `practitioner` module two things:

1. The fixed duration of the reason for that practitioner.
2. Whether the requested time falls inside their availability (weekly schedule, breaks,
   time off).

The booking domain receives a **start time and a duration**, already resolved, and knows
nothing about what the reason means. `appointment` never sees `practitioner` entities, only
its DTOs.

---

## D-09 — Integrity rules in the database as well
**Date:** 2026-09-22 · **Status:** accepted; both points revised, see the note below

On top of the validation in the service:

- A partial unique index on the national ID, which still allows many patients without one.
- An overlap check inside the booking transaction.

With three or four practitioners, an elaborate locking scheme is not justified.

**Revised.** Neither point was built as written:

- *The partial unique index was not needed.* A plain unique constraint over the document's type
  and number gives the same rule, because both H2 and PostgreSQL allow repeated nulls in a
  unique constraint, and it keeps the SQL portable (D-03). The constraint covers the pair rather
  than the number alone since the document became typed (D-26).
- *The overlap check in the database is postponed to the move to PostgreSQL.* Checking inside
  the booking transaction does not stop two concurrent bookings of the same slot; that takes an
  exclusion constraint over a time range, which H2 cannot express (D-21).

---

## D-10 — One fixed application time zone
**Date:** 2026-09-22 · **Status:** accepted

`America/Argentina/Buenos_Aires`, fixed, with `LocalDateTime` in the domain. No multi-zone
support. The clock is injected as a `Clock` so the cancellation deadlines can be tested.

The `Clock` bean and the time-zone property are introduced by the first stage that reads the
time, which is stage 4 (D-14).

---

## D-11 — Tests: unit tests for the domain plus key integration flows
**Date:** 2026-09-22 · **Status:** accepted; unit and integration tests run apart since D-44

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
**Spanish** — they work in Spanish: the screens in `templates/`, and the refusals the screens
show, translated from the English the API answers with (`ScreenMessages` and the patient
screen, D-33).

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
**Date:** 2026-09-22 · **Status:** accepted; the concurrency limit it names is closed by D-44

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
**Date:** 2026-09-22 · **Status:** accepted; the flat package superseded by D-42

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
**Date:** 2026-09-22 · **Status:** accepted; the number rules tightened by D-38

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

---

## D-30 — Booking checks the patient; no foreign keys across modules
**Date:** 2026-09-23 · **Status:** accepted

**The patient is checked when booking.** `patient` exposes `PatientReader.exists` in its
`api/`, and `appointment` asks through its own `PatientRegistryPort`, answered by an adapter in
`appointment/external/` — the same shape as the practitioner connection (D-29). An
unregistered patient is a 400: the request names someone who cannot be booked, and reception
registers them first. It is checked first, before the reason and the hours, because it is the
most basic thing that can be wrong.

`PatientReader` offers only `exists`. When the patient file needs names and documents, that is
where they are added (D-14).

Rescheduling and cancelling do not check the patient again: the appointment already exists,
and it could only have been booked for a registered patient.

**No foreign keys from `appointment` to `patient` or `practitioner`.** The comment in
`V1__appointment.sql` said they would be added once those tables existed; that is withdrawn.

- Each module owns its tables. A foreign key across modules couples their schemas the way D-05
  forbids coupling their code: `patient` could no longer change or move its table without
  touching `appointment`'s.
- The reference is already checked through the other module's api, inside the booking
  transaction.
- Nothing deletes a patient or a practitioner (see D-26 and D-28), so an existing reference
  cannot become dangling later.

The accepted risk is that the database itself does not guarantee the reference; a row written
by hand, bypassing the application, could point nowhere. That is the same trade as the one in
D-15 for `restore`.

*Rejected:* cross-module foreign keys (couple the modules' schemas); checking the patient in
the domain (the domain does not know patients, only their id).

---

## D-31 — Check-in and automatic cancellation
**Date:** 2026-09-23 · **Status:** accepted; the automatic cancellation is an absence since D-61; the check-in left the appointment for the queues in D-65

Applies D-06 and D-07. The appointment gains `checkedInAt` and `autoCancellationSuspended`,
added by `V4` with `ALTER TABLE` rather than by rewriting `V1`.

**The rules live in the appointment**, since they depend on nothing else:

- `checkedIn(at)` records the arrival; a second check-in, or one on a cancelled appointment,
  is refused.
- `withAutoCancellationSuspended()` records that the patient warned they would be late. It is
  refused once they arrived — there is nothing left to wait for — and a second time, like any
  repeated operation.
- `isDueForAutoCancellation(now)`: not checked in, still booked, and at least 30 minutes past
  the start, or 24 hours when suspended.

**Three consequences that were not spelled out before, decided here:**

- *An appointment whose patient arrived cannot be rescheduled.* The arrival belongs to that
  time, not to another.
- *Moving an appointment drops the late-arrival notice.* It was about the old time.
- *An appointment whose patient arrived can still be cancelled by hand.* That is the manual
  handling the context asks for when a practitioner turns out to be absent.

**The check-in time is the practice's clock**, not something the caller sends, so it cannot be
backdated. The `Clock` bean arrives now, with the first code that reads the time (D-10, D-14),
in `shared/TimeConfiguration`.

**The sweep narrows, the appointment decides.** The query reads booked appointments without a
check-in that started at least 30 minutes ago — the shortest wait there is — and each one
answers whether it is due. A suspended appointment inside its 24 hours is read and left alone;
a handful of rows a minute is worth keeping the rule in a single place instead of repeating it
in SQL.

**The job is a trigger and nothing else.** `OverdueAppointmentsJob` runs the use case every
minute. `@EnableScheduling` sits in `AppointmentConfiguration`, the module that needs it. The
job can be switched off with `app.scheduling.enabled=false`, and the tests do: they book on
fixed past dates, which the real clock would find overdue and cancel mid-test. The rule is
tested instead through the use case, with a fixed clock.

Endpoints: `POST /api/appointments/{id}/check-in` and
`POST /api/appointments/{id}/suspend-auto-cancellation`. Refusals are 409, like every booking
rule.

---

## D-32 — Time off, and what it does to the agenda
**Date:** 2026-09-23 · **Status:** accepted; several days in one absence since D-46

**Time off belongs to the practitioner.** `TimeOff` is a value object stored inside the
practitioner (`V5`), a whole day or part of one, with an exclusive end like every other stretch.
`AvailabilityPolicy` arrives with it, as D-28 anticipated: the week minus the time off. With two
things to combine it earns its place; with one it would have been scaffolding. Overlapping
absences are accepted, since recording the same afternoon twice leaves the practitioner just as
absent.

**The cascade belongs to the appointment module**, which is the only one that knows
appointments. `RecordTimeOffUseCase`:

- records the absence through the practitioner module's api — a new `TimeOffWriter`, apart
  from `PractitionerReader` because it writes (D-18);
- cancels every appointment the absence touches whose patient has not arrived;
- leaves the ones whose patient already arrived as they are, and reports them as needing manual
  handling, because a person is waiting and reception has to decide.

All of it runs in one transaction: the absence and its consequences are stored together or not
at all. Later bookings during the absence are refused by the availability check with a 409, like
any time outside the practitioner's hours.

**Why the practitioner module does not trigger the cascade itself.** It would have to know about
appointments, reversing the dependency D-05 set (`appointment` depends on `practitioner`, not the
other way round). A domain event would keep the direction but split one operation across a
listener, and the response could no longer say what was cancelled. The use case in `appointment`
keeps it one operation with one answer.

**The route is `POST /api/time-off`**, served by the appointment module, and not
`/api/practitioners/{id}/time-off`: a route under another module's resource would hide who
answers it. The response carries the absence, the appointments cancelled and the ones needing
manual handling.

**A day or a time range**, as the context asks: without times it is the whole day; with both it
is part of it; with only one it is a 400. An absence of several days is recorded a day at a time.

*Rejected:* the cascade in the practitioner module (reverses the dependency); a domain event
(splits one operation, loses the answer); cancelling appointments whose patient arrived (the
context asks for manual handling).

---

## D-33 — The screens
**Date:** 2026-09-23 · **Status:** accepted; "no JavaScript" relaxed by D-36; the practitioner and time-off screen added by D-46

Applies D-02. Four screens for reception, in Spanish (D-13): the agenda of a practitioner's day,
booking, moving an appointment, and patients. On the agenda, the day's operations are buttons:
"Llegó" (check-in), "Avisó demora" (late-arrival notice), "Mover", "Cancelar".

**Thin and throwaway, as D-02 required.** `AgendaViewController` calls the same use cases as the
REST controllers and never the REST controllers themselves; `PatientViewController` sits in the
patient package so the service can stay package-private. No rule lives in a screen. Deleting
`templates/` and the two view controllers removes the screens and nothing else. No JavaScript:
plain forms, and every action answers with a redirect back (post/redirect/get), so reloading the
page never repeats an operation.

**Screens ask other modules for names directly.** An agenda of UUIDs is useless to reception.
The use cases do not need names — only the screen does — so the view controller reads them from
`PatientReader` and `PractitionerReader` instead of widening the application's ports with
display data. Both api interfaces grew for this: `findAll`/`search` returning summaries.

**Refusals are translated on the screen.** The API answers in English; `ScreenMessages` turns the
known refusals into Spanish for reception. An exception that is not an expected refusal is not
hidden behind a message: it fails as it should.

**Suggested times, not a grid.** `SuggestSlotsUseCase` offers every quarter of an hour where the
practitioner works and a slot of the right length is free. Booking still accepts any start time,
through an "…u otra hora" field, as the context asks. When moving an appointment, its own slot
does not count as taken, so moving it by a quarter of an hour is offered. The suggestions look at
the practitioner's agenda only; the patient may not be chosen yet, and booking checks them anyway.

**The time-off and practitioner configuration have no screen.** Reception configures them rarely,
and they are available through the API; a screen can be added when it is asked for.

---

## D-34 — Security
**Date:** 2026-09-23 · **Status:** accepted; a practitioner asking for another agenda is refused since D-39;
the users moved to their own module in D-43

Applies D-04, the design that was built in stage 1 and removed until there was something to
protect.

**Two filter chains, one per kind of client.** `/api/**` takes basic authentication on every
request, with no session and no CSRF — the contract of the future single-page applications. The
screens take a login form, a session, and CSRF protection on every form; Thymeleaf adds the
token to each form by itself. 401 and 403 on the API carry the usual error body (D-23), and the
401 omits `WWW-Authenticate` so browsers do not pop up their own dialog.

**What a role may reach is decided by route; whose agenda is decided by the use case.** A
practitioner may only `GET /api/appointments` and the agenda screen; reception may do
everything. Which practitioner's agenda comes back is not a route question — both roles use the
same route — so `ViewDayAgendaUseCase` asks, through `AgendaAccessPort`, whom the user may see:
reception gets the practitioner it asked for, a practitioner always gets their own and the
parameter is ignored, exactly as D-04 said. The use case now answers whose agenda it is, so the
screen shows the right name even when a practitioner asked for someone else's.

On the screen a practitioner sees only their name to choose and no buttons. Hiding them is a
courtesy: the routes behind them are closed anyway, and a test posts to them directly to prove
it.

**`security` is a module like the others**, flat and package-private, exposing
`CurrentUserReader` in its `api/`. It answers one question — the practitioner the user stands
for, if any — and fails closed: a user with the practitioner role whose practitioner cannot be
told is refused, never treated as reception.

**No foreign key from `app_user.practitioner_id`**, which D-04 had planned: the practitioner
table belongs to another module, and D-30 keeps foreign keys inside a module.

**Users come from development data in a Flyway location of its own.** `db/seed` holds users,
two practitioners with their reasons and weeks, and two patients, so the application is usable
right after starting it. It is kept out of `db/migration` because the schema belongs to every
environment and this data only to development: a real deployment drops `classpath:db/seed`
from `spring.flyway.locations` and creates its users another way. No user administration screen
in v1 (D-04).

The tests authenticate as reception with `@WithMockUser`, and use the seeded users where the
difference between roles is what is being tested.

---

## D-35 — More ways to read the agenda, and a booking screen that keeps what was chosen
**Date:** 2026-09-23 · **Status:** accepted; the booking button per practitioner replaced by D-45

Asked for after v1, once reception used the screens.

**The booking screen lost what was chosen.** The patient search and the booking were two
separate forms. Searching a patient after choosing a time lost the time, and typing a name and
pressing "Reservar" sent no patient, so booking always answered "Elegí un paciente y un
horario", however the screen had been filled in. It is now **one form with two buttons**:
"Actualizar" posts everything and is redirected back to the screen with it all still chosen;
"Reservar" books. The refresh goes through a POST and a redirect rather than a GET button so
the CSRF token never ends up in a URL. A search with a single match chooses that patient, and a
refusal names what is missing ("Elegí un paciente.") instead of listing everything.

**Four views instead of one.**

- *Moving through days*: previous, today and next, keeping the practitioner and the view.
- *Everyone's day*: without a practitioner, the agenda shows every practitioner's
  appointments with a column saying whose each one is. It is now what the agenda opens on,
  so it offers a "Nuevo turno" button per practitioner — the first version showed none there,
  and booking looked impossible.
- *A practitioner's week*, Monday to Sunday, a day at a time, empty days included.
- *A patient's appointments*, cancelled ones included, the most recent first, linked from the
  patients screen and from each patient on the agenda.

**One use case for the agenda, over a span.** `ViewDayAgendaUseCase` became
`ViewAgendaUseCase`, which takes an optional practitioner and a first and last day. A day is a
span of one; everyone's is no practitioner. One use case keeps the access rule of D-04 in a
single place: a practitioner still gets their own agenda, whether they ask for someone else's
or for everyone's. The storage port gained two range reads; the daily reads that booking uses
are untouched.

**A patient's appointments are a use case of their own**, `ViewPatientAppointmentsUseCase`,
since they cross practitioners and include what was cancelled. Only reception reaches them,
decided by route (D-34): there is no "whose" question, because reception reads every patient.
The screen lives in the appointment module, which owns the data; the patients screen only
links to it. **This is not the patient file** of the planned evolution: it is the list of
appointments, which the file will contain along with much more.

**The REST API is unchanged.** `GET /api/appointments` still takes one practitioner and one
day. Nothing that consumes the API needs a span or everyone's agenda yet (D-14); widening the
endpoint is a small change when the single-page application asks for it.

*Rejected:* a separate use case per view (the access rule repeated in three places); the
agenda of everyone as a loop over practitioners in the controller (a query per practitioner,
and a rule in a screen); a `formmethod="get"` refresh button (puts the CSRF token in the URL).

---

## D-36 — A few lines of JavaScript on the booking screen
**Date:** 2026-09-23 · **Status:** accepted; extended to the agenda and moving screens by D-45

Relaxes the "no JavaScript" of D-33, for the booking screen only.

Without JavaScript the server learns about a new day or a new reason only when the form is
sent, so reception chose a reason and saw no times until pressing "Actualizar" — or
"Reservar", which then complained. Reception reported it as a bug, and from their chair it is
one.

- Changing the **day** or the **reason** presses "Actualizar" by itself: a `change` listener
  over the fields marked `data-refresh`, five lines inline, no library.
- The **patient search** refreshes on Enter, which the form already did, and not on leaving the
  field: a blur that refreshed would race a click on "Reservar".
- The **first reason comes chosen**, so the free times show as the screen opens. Reasons are
  listed alphabetically, so it is the first by name, not necessarily "consulta".
- Without JavaScript everything still works through the button.

*Rejected:* searching while typing (a fetch per keystroke and a new route returning patients,
more than the problem asks for); no JavaScript at all (the reported problem stays).

---

## D-37 — Booking a new patient from the booking screen
**Date:** 2026-09-23 · **Status:** accepted; the two transactions made one by D-75

A walk-in who has never been to the practice had to be registered on the patients screen first,
then searched on the booking screen. The booking screen now takes a new patient's name and
optional document, and "Reservar" registers them and books.

**Two operations, two transactions, for now.** The screen calls `PatientWriter.register`, a new
interface in `patient/api` (kept apart from `PatientReader` because it writes, like
`TimeOffWriter`), and then the booking use case. If the booking is refused — the time was
taken — the patient stays registered and the screen comes back with them chosen, so trying
another time does not register them twice. Registering and booking in one transaction, where
a refused booking leaves no patient behind, is **postponed**: it needs a use case in
`appointment` with a port towards `PatientWriter`, and the two-step version is enough to use
the screen today.

**Refusals cross the module boundary as the api's own exception.**
`PatientRegistrationRefusedException` carries a reason — duplicate document, incomplete
document, invalid data — so the screen can say what to fix without importing the patient
module's exceptions (D-24). `PatientReader.documentTypes()` lets the screen offer the kinds of
document without the enum leaving its package.

**A document that already exists is refused, not reused.** Reception is told the document
belongs to someone else and to pick them in the search. Silently booking that patient would be
wrong whenever the name typed does not match.

**A patient picked in the search and a new one typed in at once are refused**: which one was
meant cannot be told.

*Rejected:* redirecting to the patients screen and back (two screens for one task, the problem
reported); reusing the patient an existing document belongs to (a typo books someone else).

---

## D-38 — Refusing wrong data where it is typed, and nothing more
**Date:** 2026-09-23 · **Status:** accepted

Asked for after reception registered a patient with "DNI 544". Every entrance was then probed
with wrong data, and what got through is refused now. Each rule lives in the entity or the
domain (D-17), so the API, the screens and `PatientWriter` all go through it; the screens
translate the refusal into Spanish.

**What answered 500.** The API validated lengths with Bean Validation, but the screens and
`PatientWriter` do not go through it, so a name over 150 characters, a document number over 20
or a note over 500 reached the database and failed there. `Patient`, `IdentityDocument` and
`Appointment` now check the length of their own columns.

**Documents and names.** A DNI has 7 or 8 digits; the older LE and LC booklets, 6 to 8
digits; a CI or a passport, letters and digits only, since their formats depend on the issuing
country. Each `DocumentType` carries its own format. A full name needs at least one letter;
nothing stricter, since names carry accents, apostrophes and hyphens. This reverses D-26's "no
length check": the elderly patients with short numbers carry LE or LC, which still accept 6
digits.

**Time.**

- An appointment is booked or moved **from now on**, and no more than a year ahead. An earlier
  start was booked and then cancelled by the job within the minute; a later one is almost
  certainly a mistyped year. `BookingPolicy.checkWhen` decides it, with "now" handed in like
  the agenda (D-17), and the suggested times leave out what has already passed.
- A check-in and a late-arrival notice happen **on the day of the appointment**, decided by the
  appointment itself. Checking in on another day is the wrong line of the agenda.
- Time off is refused **for a day already gone**, but today is accepted whole: an absence
  reported at nine can start at midnight, or run from eight to noon.
- A reason lasts at most **8 hours**.

**The API answers everything in its own error body.** A body that is not JSON, a value of the
wrong type, an unknown document type, a malformed id or a missing parameter used to get
Spring's default body — or, for a malformed id, a message quoting Java internals. The shared
handler now answers them with the usual `{timestamp, status, error, message, fields?}`, naming
what is wrong and nothing of the code behind it. It answers REST controllers only and goes
first, because a module's advice would otherwise take a malformed id for one of its own
refusals through its cause.

**Authentication and authorization need nothing new.** D-34 already covers what v1 requires:
login, BCrypt, inactive users kept out, basic authentication on the API, sessions with CSRF on
the screens, routes closed by role and the practitioner's agenda restricted in the use case.
Lockout after failed logins or user administration would serve later needs, not this one.

**The tests got a clock of their own.** With the real clock, a test booking a fixed date starts
failing the day that date passes. `FixedClockConfiguration`, in the test sources, stops the
practice's clock at the start of Tuesday 10 March 2026 for every test that starts the
application; the unit tests pass a fixed clock by hand, as they already did for the deadlines.

*Rejected:* a tolerance of 30 minutes into the past for walk-ins (the practice asked for "from
now on"); refusing time off that starts before now (an absence reported in the morning is real
from midnight); validating on the screens separately (a second copy of each rule to keep in
step); a login lockout (not needed for an internal application today).

---

## D-39 — A practitioner asking for another agenda is refused
**Date:** 2026-09-23 · **Status:** accepted; made a general rule by D-50

Supersedes the part of D-04 and D-34 that ignored the parameter: until now, a practitioner who
asked for another practitioner's agenda was silently answered with their own.

That protected the other agenda, but it answered a different question from the one asked. A
client — the future single-page application, a script, a mistyped URL — gets a 200 and a list
it takes for the agenda it asked for, and nothing says it is not. A refusal says so.

- **Asking for another practitioner** is refused: `ViewAgendaUseCase` throws
  `AgendaAccessDeniedException`, the API answers 403 with the usual error body, and the screen
  answers 403. On the screen it takes editing the URL, since a practitioner is offered no one
  else to pick.
- **Asking for no one** still gives a practitioner their own agenda. That is what the agenda
  screen asks when it opens; refusing it would refuse the practitioner's home page.
- **Asking for themselves** is answered as before.

The decision stays in the use case, through `AgendaAccessPort` (D-34): the route cannot tell,
since reception and practitioners use the same one.

*Rejected:* keeping the silent substitution (a wrong answer with a success status); refusing a
practitioner who asks for no one (their home page would fail); a 404 hiding that the agenda
exists (the practitioners of a practice know each other; there is nothing to hide, only to
refuse).

---

## D-40 — Line endings fixed by the repository
**Date:** 2026-09-23 · **Status:** accepted

A `.gitattributes` makes every text file LF, in the repository and in the working tree, except
the Windows scripts (`*.cmd`, `*.bat`), which `cmd.exe` expects with CRLF.

The repository already stored LF, but what a checkout wrote depended on each machine's
`core.autocrlf`: on this one, `true`, so files rewritten by git came out CRLF while files written
by the tools stayed LF, and every commit warned that "LF will be replaced by CRLF". Settings
travel with the repository now, so another machine or a CI runner sees the same files.

It was postponed until the repository was used elsewhere; the warning on every commit was reason
enough to do it now.

*Rejected:* CRLF in the working tree on Windows (`eol` left to the platform — the mixed state this
replaces); changing `core.autocrlf` on this machine (fixes one machine, not the repository).

---

## D-41 — The appointment module laid out as its hexagon
**Date:** 2026-09-24 · **Status:** accepted

The module had grown to 22 files in `application/` and 14 in `domain/` and in `web/`, with its
adapters loose at the root beside the layers they served. It is now split by the layer and the
direction of the hexagon, not by a count of files:

```
appointment/
├── domain/                the centre; exception/ holds its refusals
├── application/
│   ├── usecase/           the use cases, and AgendaReader
│   ├── port/              the outbound ports
│   └── exception/         the use cases' refusals
└── adapter/
    ├── in/                http/ (the REST API), screen/ (Thymeleaf), scheduling/
    └── out/               persistence/, external/ (the other modules)
```

- **`adapter/in` and `adapter/out`** make the direction of every dependency visible: what calls
  the use cases, and what the ports are answered by.
- **`http/` and `screen/` are separate**: two clients of the same centre. When the single-page
  applications arrive, `adapter/in/screen/` goes with `templates/`, which is D-02 made literal.
  `http` rather than `web` or `rest`, as the practice's developer names it; `shared/web/`, which
  holds only the API's error body and its handler, became `shared/http/` for the same reason.
- **`port/` holds outbound ports only.** The way in is the concrete use cases (D-24).
- **Suffixes stay** although the folder now says what a file is: an import reads the same in
  any file, and it is Java's convention.

**What package visibility keeps together.** A subfolder is a different package to Java, with no
special access to the one above it, and a top-level class cannot be `protected`. So:
`Appointment`, `BookingService` and `BookingPolicy` stay in one package, since `book` and
`movedTo` are reachable only from there (D-17); `AgendaReader` stays package-private beside the
use cases that use it, rather than being made public to live in a folder of its own.

`usecase/` keeps twelve files: all of one kind, each named by what it does. Splitting it by topic
would make `AgendaReader` public for no gain. `patient`, `practitioner` and `security` are not
hexagonal: they are split by their own layers, in D-42.

The earlier decisions keep the paths they were written with; `appointment/external/` in D-29 and
D-30, for example, is now `appointment/adapter/out/external/`.

*Rejected:* dividing by file count (the number says nothing about where a file belongs);
`adapter/in/http/rest/` (a level with nothing to tell apart); an `api/` package for the REST
controllers (in this repository `api/` is what a module exposes to the others, D-05).

---

## D-42 — The layered modules split by layer, and ArchUnit guarding what visibility did
**Date:** 2026-09-24 · **Status:** accepted

`patient`, `practitioner` and `security` are layered, so they are split by their layers rather
than by the hexagon of D-41:

```
patient/                         practitioner/ and security/ alike
├── api/                         what other modules see (D-05), as before
├── presentation/
│   ├── http/                    the REST controllers, their Request/Response and error handler
│   └── screen/                  the Thymeleaf controllers
├── service/                     the service, and the refusals it raises
├── entity/                      the entities and value objects, and the refusals they raise
└── persistence/                 the Spring Data repository
```

- **Singular names**, like the rest of the repository.
- **`api/` stays what it was**, the contract between modules; the controllers are not part of
  it, since no other module calls them.
- **An exception lives with what raises it.** The entity's refusals are in `entity/` — the entity
  layer must not depend on the service layer — and the service's in `service/`.
- **`AvailabilityPolicy` is in `entity/`**: `Practitioner` is what uses it, so it stays
  package-private.
- **`security`** keeps `SecurityConfiguration` at its root: it configures the whole application,
  not one layer. `AppUserDetails`, the adapter Spring Security reads a user through, is in
  `service/` with those who use it. Its users moved to a module of their own in D-43.

**What this cost.** Until now these modules were one package each, and package visibility was
the guarantee: no other module could compile a reference to `PatientService`, nothing but the
service could reach the repository, and nothing but the service could change an entity (D-17,
D-24, D-25). Split into packages, whatever one layer uses of another has to be public: the
services and the methods the controllers call, the entities and their getters, the entities'
changing methods for the service, the value objects the requests build, the exceptions the
handlers catch.

**What gets it back.** `ArchitectureTest`, with ArchUnit, checks the compiled classes:

- a module is reached from another only through its `api/` — all four modules, `appointment`
  included, which D-05 had left to convention;
- in the layered modules, presentation reaches only the service, and only the service reaches
  persistence;
- no class outside `service/` and `entity/` calls a method of an entity other than a getter.

Each rule was checked to fail by planting a violation of it. The guarantee moves from the
compiler to the build, which fails all the same.

*Rejected:* keeping the modules flat (it was asked to split them by their architecture);
splitting them by the hexagon (not their architecture); a `persistence/` package with no
check (anything could have injected a repository and written past the service); separating
authentication from authorization (configured together by Spring Security, in one class).

---

## D-43 — Users are a module of their own
**Date:** 2026-09-24 · **Status:** accepted

`security` held two things: the access rules of the whole application — the filter chains,
the login, the 401 and 403, who is asking — and the users those rules authenticate. The users are
expected to change a lot: administering the practice's staff, and patients' accounts for web
booking (see the context). They now live in `user`, layered like the other modules (D-42):

```
user/
├── api/          UserAccountReader, UserAccount, Role
├── service/      UserAccountService, implementing the api
├── entity/       AppUser
└── persistence/  AppUserRepository

security/         the access rules; asks user/api for who someone is when they log in
```

- **`security` reaches users only through `user/api`**, as any module reaches another (D-05):
  `AppUserDetailsService` asks `UserAccountReader.findActiveByUsername` and wraps the answer for
  Spring Security. `security` has no entity and no storage left.
- **`Role` is part of `user`'s contract.** A user has a role, and `SecurityConfiguration` decides
  routes by it. The `ROLE_` prefix is Spring Security's convention, not the user's, so it moved
  out of `Role` into `security` (`AppUserDetails.authorityOf`).
- **`UserAccount` carries the password hash** across the boundary, since checking it is
  security's job. It is the hash, never the password, and only `security` asks for it.
- **The `app_user` table** now belongs to `user`. The migration that created it (`V6`) is left as
  it is: the schema did not change, only which module owns it.
- **Authentication and authorization stay together** in `security`: Spring Security configures
  them in one filter chain, and splitting them would add a boundary between two things that
  always change together.

This was first recommended against — "it will change" is the reason D-14 rejects — and then
decided the other way: the split is mechanical, it changes no behaviour, and it gives the user
features that are coming a module to land in.

*Rejected:* naming the rest `gateway` (the word usually means an API gateway, or an adapter to an
external system; `security` says what it is); separating authentication from authorization.

---

## D-44 — PostgreSQL for the application, H2 for the integration tests, and the tests split
**Date:** 2026-09-24 · **Status:** accepted

Carries out what D-03 prepared and closes the limit D-21 named.

**The application runs on PostgreSQL 18**, started by `docker-compose.yml` with a volume, so the
data outlives a restart. The datasource in `application.yml` matches it. No production
configuration yet: there is no deployment to configure (D-14).

**The database refuses overlapping appointments itself.** Two receptionists booking the same slot
at the same time could both find it free, since each read the agenda before the other wrote.
`V7` adds two exclusion constraints over `[start, start + duration)` of booked appointments: one
per practitioner, one per patient. The persistence adapter flushes on save, so a refusal
surfaces inside it, and answers it as `PractitionerAlreadyBookedException` or
`PatientAlreadyBookedException` — the client gets the same 409 whichever check caught it. The
constraint's name is read from the driver's message, because Hibernate does not extract it for
exclusion constraints. `btree_gist`, which lets the index compare uuids beside the range, ships
with PostgreSQL; creating it takes a role allowed to create extensions.

**What only one engine can express lives in `db/vendor/{vendor}`.** Spring Boot fills in the
engine's name, so `V7` runs on PostgreSQL and not on H2. Everything else stays in portable SQL
in `db/migration`, and D-03's rule holds for it.

**Three kinds of test, run apart.** Tests that need nothing installed should not wait on anything
that is:

| Kind | Name | Runs on | Needs |
|---|---|---|---|
| Unit: domain, use cases with fakes, pure rules, architecture | `*Test` | `mvnw test` (Surefire) | nothing |
| Integration: `@SpringBootTest`, `@DataJpaTest` | `*IT` | `mvnw verify` (Failsafe) | nothing — H2 in PostgreSQL mode |
| PostgreSQL: what only PostgreSQL can answer | `*PostgresIT` | `mvnw verify` | Docker; skipped without it |

The integration tests keep H2, as asked, so they run anywhere. The one thing H2 cannot answer is
the exclusion constraint, so `AppointmentOverlapPostgresIT` checks it against a throwaway
PostgreSQL started by Testcontainers. It stores overlapping appointments through the adapter,
past the application's check — exactly what a race amounts to — which is deterministic where
racing two threads would not be. It also runs every migration on a real PostgreSQL.

*Rejected:* PostgreSQL for every integration test through Testcontainers (every test run would
need Docker); leaving the constraint untested because H2 cannot run it (a rule in the database
with nothing checking it); a pessimistic lock on the practitioner's day instead of the
constraint (it serialises every booking of the day and still leaves the patient's rule open).

---

## D-45 — The agenda never disagrees with what is chosen, and the practitioner is chosen when booking
**Date:** 2026-09-24 · **Status:** accepted

Reception reported a flow that booked with the wrong practitioner. On the agenda of Dr. Benítez,
they picked Dra. Arias in the list and, without pressing "Ver", pressed "Nuevo turno": the list
said Arias, the page was still Benítez's, and the button — built with the page — opened a booking
with Benítez. Nothing on screen said whose agenda it was.

- **The agenda's choices apply as they change** — practitioner, day and view — with the few lines
  of D-36, now a shared fragment of the screens. The moving screen's day does the same. "Ver"
  and "Ver horarios" remain for a browser without JavaScript.
- **The agenda says whose it is** in its heading: "Agenda de Dra. Laura Arias", or "Agenda de
  todos los profesionales". Even without JavaScript, the page can no longer look like someone
  else's.
- **One booking button.** From a practitioner's agenda it says "Nuevo turno con …" and comes with
  them chosen; from everyone's it says "Nuevo turno". It replaces the button per practitioner of
  D-35.
- **The practitioner is a choice on the booking screen**, like the day and the reason. From
  everyone's agenda it starts unchosen and booking asks for it; changing it keeps the day, the
  patient and the note, and brings the new practitioner's reasons, their first one chosen when
  the reason chosen before was not theirs.
- **A day off is not a full day.** The suggestions also tell whether the practitioner works that
  day at all — their week and their time off — so the screen says "Dr. Martín Benítez no atiende
  ese día" instead of "No quedan horarios libres", which read as a booked-out day. It is only
  asked when there are no times to offer, so a normal day costs nothing more.

*Rejected:* keeping "Ver" as the only way to apply the agenda's choices (the reported bug);
disabling "Nuevo turno" until "Ver" is pressed (the same trap, one step later); a booking screen
that starts with the first practitioner from everyone's agenda (it would choose for reception).

---

## D-46 — A screen for practitioners and their time off, and absences of several days
**Date:** 2026-09-24 · **Status:** accepted

Reception configures practitioners and records their absences, but until now only through the
API (D-33), which reception does not use. With this screen, v1 is usable end to end from the
screens alone.

**The practitioner screens live in `practitioner/presentation/screen`**, like the patient screen
in its module: `/practitioners` lists them and registers one; `/practitioners/{id}` edits the
name, the reasons — each changed in place, one more added below; none deleted, as in the API
(D-28) — and the week. The week is a table of stretches plus three empty rows and is saved whole,
as the API replaces it; emptying a row takes its stretch out, and a half-filled row is refused
rather than guessed. Refusals come back in Spanish. A "Profesionales" link in the header shows
for reception only, and the routes are reception's by the general rule of
`SecurityConfiguration`.

**The absence form posts to the appointment module** (`/time-off`, `TimeOffViewController`),
since what matters is the cascade over the appointments, which only that module knows (D-32). It
goes back to the practitioner saying what it did: the appointments cancelled, and — as an error,
so it stands out — the patients already there, for reception to handle. The practitioner page
lists the coming absences.

**An absence can span several days.** From a day until another, whole days; or part of a single
day between two times. A span such as Monday afternoon to Wednesday noon is not offered: it can
be recorded as two absences, and would complicate the form for a rare case. The rule lives once,
in `RecordTimeOffUseCase.Command.of`, which the API and the screen both use; the API gains an
optional `untilDate`. Nothing deeper changed: the use case, `TimeOff` and the cascade already
worked on a span, day by day.

A day already gone is refused with its own exception, `TimeOffInThePastException`, still a 400
on the API, so the screen can say it without reading a message. A practitioner's name now checks
its length in the entity too, as D-38 did for patients: the screen reaches it without the API's
Bean Validation.

*Rejected:* the practitioner module serving the absence form (it would have to know
appointments, reversing D-05's direction); spans across days with times; deleting or editing an
absence (not in the API either, and the appointments it cancelled would not come back by
themselves — rules of their own, for when it is asked for).

---

## D-47 — Consultations are a module of their own, hexagonal
**Date:** 2026-09-24 · **Status:** accepted; the consultation gains addenda in D-52; the module renamed `clinicalrecord` as part 3 began, as D-52 planned

v2 starts with the clinical record (see the context): what a practitioner did in each
consultation, which the other practitioners of the practice read. It is not the appointment:
an appointment is only a booking, and once the arrival queue exists a consultation will not
always come from one. So it gets a module, `consultation`, which knows nothing of appointments
beyond one question (D-48).

**Hexagonal, like `appointment`,** because it has rules of its own to protect: what is sent is
never changed, and who may read which patient is decided by a rule with a period and today's
visits in it (D-15, D-16). The layout is D-41's: `domain/`, `application/usecase`, `port`,
`exception`, and `adapter/in/http`, `in/screen`, `out/persistence`, `out/external`.

**`Consultation` is created by one factory that checks itself, and nothing changes it.** It
holds free text about what was consulted (`notes`, required, up to 10 000 characters) and up to
50 items, each with a kind — `CHECK`, `PRESCRIPTION`, `STUDY_ORDER` — and a free-text
`description` of up to 2 000. A list of typed items rather than a fixed text field per kind:
giving one kind a structure later touches that kind alone, and a consultation with three
prescriptions needs no extra columns. There is no mutating operation, so nothing has to stay
package-private: the addenda of the next part will add to a consultation without changing it.
The storage adapter reads back through the same factory, so no invalid consultation can be
obtained, as with `Appointment.restore`.

**No domain service.** "The practitioner is seeing this patient today" decides *who may*, not
what a valid consultation is, so it is an access rule applied by the use case, like the agenda's
(D-39), through the policy of D-49.

**The time of recording is the practice's clock**, never something the caller sends, so a
consultation cannot be backdated, like the check-in (D-31). It also makes "sent on the day the
patient is seen" hold by construction: it can only be sent while the patient has arrived today.

**`V8__consultation.sql`**, portable: `consultation` and `consultation_item`. The item table has
a foreign key to its consultation, which is the same module; there is none towards the patient
or the practitioner (D-30).

*Rejected:* one text field per kind (columns to migrate the day any kind is structured);
consultations inside the appointment module (an appointment is only a booking); the layered
layout (the rules are worth a pure domain).

---

## D-48 — "Is the practitioner seeing this patient today?" is a port of `consultation`
**Date:** 2026-09-24 · **Status:** accepted; the port is answered by the queues too since D-63, and the check-in left the appointment in D-65

The check-in has lived on the appointment since v1 (D-31), though the arrival is not the
booking's business. It stays there until the arrival queue, which brings a second way of
arriving and will take the arrival over. Until then `consultation` asks through a port of its
own, `AttendancePort.visitToday(practitioner, patient, day)`, which answers `NONE`, `EXPECTED` —
a booked appointment that day — or `ARRIVED` — one with a check-in. When the queue arrives, only
the adapter behind the port changes.

**`appointment` gets its first `api/`:** `AppointmentReader.findBookedForPatientOn(patient, day)`
and the DTO `AppointmentSummary` (id, patient, practitioner, start, whether checked in). It
answers facts, not meanings: what they mean for a consultation is for `consultation` to decide.
`consultation` depends on `appointment/api`; `appointment` never depends on `consultation`.

**A hexagonal module's `api/` is implemented by a driving adapter,** `adapter/in/external/`, the
mirror of `adapter/out/external/`: called by another module instead of by HTTP, it calls a use
case (`FindPatientDayUseCase`) like any other driving adapter. The name is provisional and may
change when a second one appears.

*Rejected:* `consultation` keeping the appointment's id or reading its state (it would be tied to
appointments, which the queue will not have); moving the check-in out of `appointment` now (a
redesign of D-31 and D-32, with a dependency cycle to solve, before the queue that justifies it).

---

## D-49 — Who reads a clinical record, and the access period
**Date:** 2026-09-24 · **Status:** accepted

A practitioner may read the whole clinical record of a patient — every practitioner's
consultations — when they recorded a consultation of that patient within the access period, or
when they have a booked appointment with the patient today, even before the patient arrives. A
future appointment grants nothing before its day. Recording a consultation needs more: the
patient has arrived.

**The rule is `ClinicalRecordAccessPolicy`, in the domain,** a plain class tested without
Spring. It is given the last consultation this practitioner recorded of this patient, today's
visit and today's date. With a last consultation on 10/03/2026 and a period of a year, the
record is open until 10/03/2027 included.

**The period is configuration, not code:** `app.clinical-record.access-period`, an ISO-8601
`java.time.Period` (`P1Y`), so a calendar year whatever the leap years. It is parsed in
`ConsultationConfiguration`, which builds the policy, as `app.time-zone` is read for the clock.

**Who asks comes from `security/api/CurrentUserReader`**, through `CurrentPractitionerPort`. A
user who is not a practitioner is refused: reception cannot reach these routes anyway (D-51), and
the use case fails closed rather than trusting that.

**An unknown patient is a 403 too, not a 404,** so the record never tells someone who may not
read it whether a patient exists. A retried consultation id that belongs to another
practitioner's consultation is refused the same way.

*Rejected:* the period hard-coded (it is expected to change); counting from the last appointment
rather than the last consultation (a consultation is the only proof the patient was seen);
reception reading the record.

---

## D-50 — Asking for what one may not see is a 403, across the application
**Date:** 2026-09-24 · **Status:** accepted

The general form of D-39. Whatever a user may not see — a route closed to their role, another
practitioner's agenda, a clinical record they have no access to — is refused with a 403 and the
usual error body, or the error page on the screens. It is never answered as if it were empty,
nor with something else in its place: a wrong answer with a success status is worse than a
refusal.

---

## D-51 — Consultation routes, API and screens
**Date:** 2026-09-24 · **Status:** accepted

**By route, the consultation module belongs to the practitioners:** `/api/consultations/**`,
`/api/clinical-records/**`, `/consultations/**` and `/clinical-records/**` take the
`PRACTITIONER` role. They are the first routes reception cannot reach.

**API.** `POST /api/consultations` takes `{id?, patientId, notes, items: [{kind, description}]}`
and answers 201 with a `Location`; the id is optional and makes a retry safe, as with booking
(D-19). The practitioner is whoever sends it, never a field of the body. `GET
/api/clinical-records/{patientId}` answers the patient's consultations, newest first, each with
its practitioner's name, read from the practitioner module's api by the controller: the use
cases need no names (D-33). The record has a route of its own rather than one under
`/api/patients/…`, for D-32's reason: a route under another module's resource hides who answers.

**Screens.** On a practitioner's agenda, today's appointments link the patient's name to their
clinical record, and those whose patient arrived offer "Empezar consulta". It opens a view with
the form beside the record. The items are rows, without JavaScript: the rows filled in plus three
empty ones, at least five, and a "Más renglones" button that brings the form back with three
more. An empty row is dropped; a row with only a kind or only a text is refused rather than
guessed, as in D-46. A refused form comes back with everything typed, through a flash attribute,
since the notes are too long for a URL.

---

## D-52 — Addenda belong to the consultation, and two at once do not overwrite each other
**Date:** 2026-09-25 · **Status:** accepted

An addendum corrects or completes a consultation already sent: its author or any other
practitioner with access to the patient's record writes one, at any time. It carries its time
and author, holds up to 2 000 characters of free text about the consultation as a whole — an item
it refers to is named in the text — and is not added to other addenda.

**The addendum is part of the `Consultation` aggregate.** It clarifies that consultation, not the
record. `withAddendum` returns a new consultation with one more addendum at the end and
everything already written unchanged; an addendum is written after the consultation and after
the addenda before it, and each id is added once. `Consultation.restore` reads a stored one back
through the same checks, as `Appointment.restore` does, next to `record` for a new one. No domain
service: who may add one is the record's access rule (D-49), applied by `AddAddendumUseCase`. A
consultation that does not exist is a 403, like one the user may not read.

**`POST /api/consultations/{id}/addenda`** with `{id?, text}` answers 201 with the whole
consultation; the id makes a retry safe (D-19). On the record screen, each consultation shows its
addenda and a form to add one.

**Two addenda at the same time do not overwrite each other.** The consultation row has a
`@Version` (`V9`). The adapter adds the new addenda to the row read earlier in the same
transaction and flushes: if someone else added one meanwhile, the version no longer matches and
the write is refused as `ConsultationChangedException`, a 409. Nothing is saved, and sending it
again works. The screen says so and keeps the text.

**The final user interface must not lose the text on a 409.** The single-page applications that
replace these screens keep what was typed and send it again, by hand or by themselves. Noted
here because the Thymeleaf screens only need to show the text again.

**The clinical record is a concept apart from the consultation.** The access rule is the
record's; the addenda are the consultation's. The module keeps its name, `consultation`, until
part 3: checks whose result arrives later make the record gather more than consultations, and the
module becomes `clinicalrecord` then, not before (D-14).

*Rejected:* addenda as an aggregate of their own (they only exist on a consultation, and the
record reads them with it); a limit in time to add one (the user wants none); addenda to addenda;
no concurrency guard (the second of two simultaneous addenda would silently erase the first);
renaming the module now, before the second kind of entry that justifies it.

---

## D-53 — A check is an aggregate of its own, and the clinical record gathers consultations and checks
**Date:** 2026-09-25 · **Status:** accepted; addenda on checks brought forward in D-58

A check is a procedure, not an encounter: it is done, and reported. Its result may arrive later
and come from someone else — a blood sample drawn by one practitioner and analysed by another —
and, as a precaution for an emergency, a check may be done with no consultation at all. So it is
not an item of the consultation any more but an aggregate of its own, `Check`, in the module that
holds the patient's clinical record, renamed `clinicalrecord` as D-52 planned.

**What one check is.** What is done once and reported once, by one author. An electrocardiogram is
one check, whose report includes the heart rate the device measures; the heartbeat heard with a
stethoscope is another. If parts of a study are reported apart, at different times or by different
people, they are different checks.

**Its states follow from its result.** `Check.perform` records one whose result is pending;
`Check.performWithResult` one whose result is known at once, reported by whoever did it.
`withResult` adds the result later; a check has one, and a second is refused with
`ResultAlreadyRecordedException`, a 409. `CheckResult` — author, time, free text of up to 10 000
characters — is a class of its own so that giving results a structure changes it alone. A check
records the consultation it was done in, when there was one.

**Who does what.**
- Checks done during a consultation are sent with it and stored in the same transaction.
- A check without a consultation, `POST /api/checks`, takes the consultation's rule: the patient
  arrived today to be seen by that practitioner. An emergency without a registered patient or an
  appointment waits for the arrival queue.
- A pending result, `POST /api/checks/{id}/result`, is recorded by any practitioner who may read
  the record (D-49): the patient need not be there. A practitioner who never saw the patient — the
  analyst — gets access through the authorization of part 3b.
- Doing a check counts as seeing the patient for the access period, like recording a consultation.
  Reporting a result does not. The reads the rule needs are gathered in
  `ClinicalRecordAccessReader`, package-private in the use cases, as `AgendaReader` is in
  appointment.

**Checks from elsewhere.** An order authorizes a check that may be done here or at another
practice, and checks done elsewhere will come in later through an anti-corruption layer. Today
whoever does a check is one of the practice's practitioners; nothing else in the model assumes it.

**Storage.** `V10` creates `clinical_check` — `check` is a reserved word — with the result in three
columns that are all empty or all set, a foreign key to the consultation (same module), and a
version: two results recorded at the same time are refused as the second one, as with addenda
(D-52). The `CHECK` item kind is removed. The database starts empty for this version, as agreed:
no check item is carried over, and a development database must be wiped.

**The record's answer.** `GET /api/clinical-records/{patientId}` answers `{consultations, checks}`:
each consultation carries the checks done during it, and `checks` holds those done without one.
Nesting them spares a client pairing checks with consultations. On the screen, checks show under
their consultation, the others by time among the consultations, a pending one with the form for
its result; a check without a consultation is recorded from the record screen.

*Rejected:* checks as items with a result added later (a check without a consultation would have
nowhere to live); keeping immediate checks as items and only pending ones apart (two ways to
record one thing); several results per check (not needed yet: a check has one); carrying the old
check items over (the database starts empty); addenda on checks now (after part 3d).

---

## D-54 — Asking for access to a record to report a check
**Date:** 2026-09-25 · **Status:** accepted

Whoever analyses a sample may never have seen the patient, so by D-49 they cannot read the record
nor report the result. They ask for access, and the practice will later decide who authorizes it
and why. For now it is granted at once.

**Only for a pending check.** With an automatic approval, a request for any patient would let any
practitioner open any record at will. So access is asked to report one of the patient's pending
checks, `POST /api/checks/{id}/access-grants`: none for a check that has its result (409) or does
not exist (404). When approvals are real, asking for other reasons can be allowed then.

**Everything is recorded, and shown.** `AccessGrant` keeps who asked, for which patient and check,
when, until when, why, and how it was approved (`Approval.AUTOMATIC`, the only value today). Every
grant is kept, valid or not, and the record shows them under "Accesos autorizados" and in the
API's `accessGrants`: whoever reads a record sees who else opened it.

**The rules are `AccessGrantPolicy`'s, and two are settings.**
- The grant lasts `app.clinical-record.access-grant.period` from the day it is granted, a year
  today, whether or not a duration turns out to be needed.
- `app.clinical-record.access-grant.reason-required` says whether a reason is required. The law
  decides that, and it is not known yet, so it is a setting, required by default. Required or
  not, the reason is never judged: nobody checks what it says.

**A grant is a third way to read.** `ClinicalRecordAccessPolicy` opens the record to a practitioner
who saw the patient within the access period, who is seeing them today, or who holds a valid
grant. A grant does not allow recording a consultation or a check: those need the patient there.
Asking again while already holding access is accepted and recorded, like any request.
`ClinicalRecordAccessReader` became a component, given to the use cases, now that it reads grants
as well.

**The list of pending results**, `GET /api/checks/pending` and the "Resultados pendientes" screen,
shows every check of the practice waiting for a result — only the patient and the check, nothing
else of the record — with "Ver historia" or "Pedir acceso" as the user may already read it or not.

*Rejected:* asking for any patient (an automatic approval would be an open door); a required reason
hard-coded, or an optional one (the law decides, and it is not known yet); grants that never end
(kept, but for a year); hiding grants from the record (who opened a record should be visible).

---

## D-55 — An order authorizes a check, and one check fulfils it
**Date:** 2026-09-25 · **Status:** accepted

A practitioner who needs a check they cannot do authorizes it with an order. Who does the check
does not matter — here, or at another practice. So an order is an aggregate of its own,
`CheckOrder`, in the clinical record, and the study-order item leaves the consultation, which keeps
only prescriptions as items.

**Its life.** It is issued during a consultation — without one, later — and is `OPEN` until one
check fulfils it (`FULFILLED`) or its author cancels it with a reason of up to 500 characters
(`CANCELLED`). One order, one check. Only an open order is fulfilled or cancelled; otherwise
`OrderNotOpenException`, a 409. Anyone else trying to cancel it gets `NotTheOrderAuthorException`,
a 403, and an order that does not exist is refused the same way. It does not expire.

**How it ended is a type, not loose fields.** An open order has no `OrderOutcome`; a closed one has
exactly one: `Fulfilled(check)` or `Cancelled(at, reason)`, a sealed interface. A cancelled order
cannot lack its reason, nor a fulfilled one its check, because the type demands them, and a
`switch` over the outcome is exhaustive. `status()` follows from it. The first version had three
nullable fields — the check, the time, the reason — kept consistent by hand, and two methods named
`fulfilledBy`: the operation and the getter; the outcome removed both problems.

**The order names the check that fulfilled it, not the other way round.** A check stays what it
was, whether it fulfils an order, was done with none, or will come from elsewhere; it needs no new
field. The check must be of the order's patient and done after it — the order's rule.

**Fulfilling one.** A check recorded with a consultation or on its own may name one of the
patient's open orders (`orderId`); without a description it is what the order asks for. The check
and the fulfilled order are stored in one transaction. Two checks fulfilling one order at the same
time are refused as the second, by the order's version (`V12`), and the database also keeps one
check from fulfilling two orders.

**Orders from elsewhere wait.** One fulfilled at another practice stays open: none of this
application's business yet. One coming from elsewhere — on paper, or from another practice — will
need validating, and comes with the anti-corruption layer; meanwhile its check is recorded with no
order, naming it in the description.

**API and screens.** `POST /api/consultations` takes `orders: [{description}]` and `checks[].orderId`;
`POST /api/checks` takes `orderId`; `POST /api/orders/{id}/cancel` takes `{reason}`. The record shows
each consultation's orders and where they stand. The consultation form has an "Órdenes" section,
and both check forms offer the open orders.

**Storage.** `V12` creates `check_order` — `order` is a reserved word — with checks that keep status,
fulfilment and cancellation consistent, and drops the study-order item kind. The database starts
empty again: no item is carried over.

*Rejected:* the check naming its order (a field on every check for what only some have, and one an
external check would not); several checks per order (one order per check, as agreed); orders that
expire (not asked for); cancelling without a reason, or by someone else; recording orders from
elsewhere now (they need a validation not yet designed).

---

## D-56 — Whoever issued orders follows them up
**Date:** 2026-09-25 · **Status:** accepted

A practitioner who issues orders needs to know which are still waiting and which came back. So
"Mis órdenes" lists the user's own orders that were not cancelled — cancelled ones wait for
nothing — each in one of three situations: `OPEN`, nobody did the check; `AWAITING_RESULT`, the
check was done and its result has not arrived; `REPORTED`. `ListMyOrdersUseCase` answers it, with
whether the user may still read each patient's record; `GET /api/orders/mine` returns it.

**Where each order stands, never what its result says.** The list names the patient, the order,
who did the check and who reported it, and when; the result is in the record, behind the access
rule, with a "Ver historia" link only while the user may read it. The same line as the list of
pending results (D-54).

**The screen splits it in two.** What waits, the oldest first, since it is what is overdue; and
what was reported, the newest first, from the last 30 days unless all are asked for. Nothing marks
a result as seen, so how recent it is stands for how new it is. The window is the screen's; the API
answers everything.

**Marking a result as reviewed is left for later.** It is a state of each practitioner's own: the
users already stand for their practitioners, so nothing blocks it, but how it should work is not
decided.

`V13` indexes the orders by who issued them, the read this list makes.

*Rejected:* listing cancelled orders (they wait for nothing); showing result texts in the list
(the record's access rule would be bypassed); every result with no window (the list would only
grow, with nothing telling new from seen); a "reviewed" mark now (undecided).

---

## D-57 — Attached files: described in the record, stored behind a port
**Date:** 2026-09-25 · **Status:** accepted

A consultation or a check may carry files: a photo, an electrocardiogram trace, a laboratory's
PDF. Any practitioner who may read the record attaches them, at the time or later — adding to the
record, like an addendum, never changing it. Nothing attached is deleted: a file attached by
mistake is said so in an addendum. Only practitioners attach files (D-51).

**`Attachment` is an aggregate of its own.** It belongs to one thing, `AttachmentOwner`, a sealed
type — `OfConsultation` or `OfCheck`; a file of the record as a whole, a report brought from
elsewhere, will be a third case, with the orders from elsewhere. It keeps the patient, who uploaded
it and when, the name without any path the browser sent, the kind, the size and a SHA-256
fingerprint, which tells the stored content is the one uploaded. It holds no bytes.

**The kind is told by the content.** `FileKind` reads the first bytes — PDF, PNG, JPEG — so a file
named `.pdf` that is not one is refused (`UnsupportedFileException`, 415). A file is at most 10 MB
(`FileTooLargeException`, 413). Spring's multipart limit sits a little higher, 11 MB, so a file just
over the limit still reaches the record and is refused with its message. Downloads are sent as
attachments with `X-Content-Type-Options: nosniff`, only to whoever may read the record, never
from a public address.

**The content lives behind a port, `AttachmentContentStore`**, keyed by the attachment's id.
Today its adapter keeps the bytes in PostgreSQL, in `attachment_content`, apart from `attachment`
so listing a record never reads the files; the file and what describes it are written in one
transaction and backed up together. When the files outgrow the database, an adapter over an object
store (S3, MinIO) replaces this one, and nothing else changes — except that the store will not join
the transaction: the file will be written first, and what a failed write leaves behind cleared.
That is decided with the store.

**A check's result may be only its attachments.** `CheckResult.text` is optional, and
`Check.withResult(result, hasAttachments)` refuses a result with neither text nor file.

**API and screens.** `POST /api/consultations/{id}/attachments` and `POST /api/checks/{id}/attachments`
take one file as `multipart/form-data`; `GET /api/attachments/{id}` downloads it. The record lists
every attachment in `attachments`, each saying what it belongs to — a flat list, so the answers of
consultations and checks stay as they were. On the screen, each consultation and check lists its
files and takes new ones, and the result form takes a file.

*Rejected:* files on disk (not transactional, backed up apart, awkward with more than one
instance); an object store now (infrastructure the practice does not need yet); trusting the
declared type; deleting or marking files (the record only grows); files as a check result's own
field (a file is attached to the check, and may come before its result).

---

## D-58 — Addenda on checks
**Date:** 2026-09-25 · **Status:** accepted

D-53 left addenda on checks for after part 3. Testing by hand showed why they cannot wait: a result
recorded by mistake — "esto es falso, debe eliminarse" — had no way to be corrected, since a result
never changes, and a check done without a consultation had no consultation to add a note to.

**The rule is the consultation's (D-52).** Any practitioner who may read the patient's record adds
an addendum to a check, pending or reported, at any time: its time and author, up to 2 000
characters, added at the end, nothing already written changed. `Check.withAddendum` keeps them in
order and each once, reusing `Addendum`. A check that does not exist is refused like one the user
may not read; a retry with the same id adds nothing. `POST /api/checks/{id}/addenda`; the check's
answer carries its `addenda`; the record screen shows them under each check with a form to add one.

**The version guards both.** `V15` stores the addenda in `check_addendum`, in order. The check's
version, which guarded its result, now guards its addenda too, so the adapter refuses a concurrent
write as `CheckChangedException` (409) whatever changed. The result's use case still answers it as
`ResultAlreadyRecordedException`, since for a result that is what it means.

*Rejected:* editing or deleting the result (the record only grows); addenda only through the
consultation (a check without one would have nowhere to be corrected).


---

## D-59 — Every reading of a clinical record is recorded
**Date:** 2026-09-26 · **Status:** accepted; the admins read them since D-71, and each keeps its user; refusals recorded since D-72

The law asks that access to a clinical record be traceable: every action on it tied to one person,
leaving a trace (Ley 27.706; Ley 26.529, art. 2). Phase 1 recorded who wrote and who was granted
access, not who read. Phase 2 opens the record in more ways — a patient waiting in a queue, an
absent appointment — so the readings are recorded first, over today's access, before any of them
arrives.

**A reading is what hands clinical content to someone:** the record shown whole
(`ViewClinicalRecordUseCase`, which serves the API and the screen, and the screen shows it again
when a form comes back refused) and an attached file downloaded (`DownloadAttachmentUseCase`).
"Resultados pendientes" and "Mis órdenes" are not readings: they show the patient, the check and
where it stands, never its content, and they exist to be seen without access. Writing is not a
reading either: whatever is written already records its author and time, and a writer read the
record first, which is recorded.

**What is kept:** `RecordReading` — who, whose record, when by the `Clock`, by which access, and the
attachment when that is what was read — in `record_reading` (`V16`), only ever inserted.

**The access comes from the rule itself.** `ClinicalRecordAccessPolicy.readAccess` answers by which
access the record is open — `VISIT_TODAY`, `ACCESS_GRANT` or `SEEN_WITHIN_PERIOD` — or nothing;
`mayRead` is that answer being present. When several apply, the first in the rule's order is the
one recorded. No second place repeats the rule to find out why it said yes. The queue will add a
way in phase 2's part 5.

**A reading that cannot be traced does not happen.** The reading is saved in the same transaction
as the read, so the two use cases become writes (`@Transactional`, D-27): if saving fails, the
record is not shown.

**Nobody reads it yet** — not on a screen, not through the API; admins will, when they exist. So
it is written through `RecordReadingWriter` alone, with no reader, no endpoint and no index beyond
the key (D-14, D-18): what reads it, and so what to index, is decided with the admins.

**Refusals are not recorded.** A 403 hands nothing over, so it is not a reading. Recording the
attempts, to notice someone probing records that are not theirs, is a security matter of its own and
is left for a later stage.

*Rejected:* recording in the controllers (the API and the screen would each have to remember it);
an event listener after the read (a reading could happen with no trace if it failed); recording the
lists of pending checks and orders (no clinical content in them); showing the readings to the
record's authors (it would show a practitioner what others do, and who reads the log is the
admins').

---

## D-60 — Nobody is turned away for lack of an identity
**Date:** 2026-09-26 · **Status:** accepted

The right to be seen comes before the paperwork (Ley 26.529, art. 2): a patient who will not
identify themselves is still seen, and their clinical record is still kept. The patient already
could come without a document (D-09); now they may come without a name, and the practice says how
far it knows who each patient is.

**One state, not two flags.** `IdentityStatus` is `ALIAS` — no name given —, `DECLARED` — a name,
maybe a document, that nobody saw — or `VERIFIED` — reception saw the document. A patient cannot be
nameless and verified at once, which two booleans would allow. Every patient registered before
this is `DECLARED` (`V17`); the column keeps that default for rows written outside the
application, such as the development data, which cannot be edited once applied.

**The alias** is `NN-` and four random digits, drawn until a free one comes up; a unique
constraint is what guarantees it, as for the document. It stands as the patient's name until they
give one, and is kept afterwards: a note that says NN-4821, written during an emergency, still
finds the patient in the search. Reception registers such a patient from the patients screen or
the API; the booking screen does not offer it, since whoever books ahead can give a name, and the
arrival queue will.

**One way to change who a patient is.** `Patient.changeIdentity(name, document, documentSeen, when,
by)` replaces renaming and setting the document, and says what changed:

- `documentSeen` verifies, and needs a document and a name other than the alias: a document is
  checked against a name;
- while the name is the alias, the patient goes by their alias;
- a verified patient stays verified while name and document stay as they were, and is declared
  again once either changes, unless the document is seen again in the same change: nobody saw the
  new one. Saving an unchanged verified patient without ticking the box does not undo it.

Registration may verify at once, since reception usually has the document in hand.

**Every change is kept**, in `patient_identity_change`: before and after of the name, the document
and the state, when by the `Clock`, and who — saved with the change, in the same transaction. A
save that changes nothing leaves nothing. The patient stays the same one: the clinical record
points at them by an id that never changes and copies no name, so no identity change touches
anything clinical. Reception sees the history on the patient's screen and through
`GET /api/patients/{id}/identity-changes`: it is not clinical content, and it tells why someone is
on the books as NN.

**Who is the username**, which `security/api/CurrentUserReader.username()` now gives. The user
module has no id to offer other modules, and a username does not change while there is no user
administration; when there is, this is reconsidered.

**The warning is in the record.** The clinical record and the consultation form say "Identidad no
verificada" while nobody has seen the patient's document, through `PatientSummary.identityVerified`.

*Rejected:* a patient with no name at all (every screen and module shows one); an alias that
disappears when the name arrives (the notes written with it would lose the patient); keeping the
verification when the document changes (nobody saw the new one); merging two records of the same
patient, for when someone registered as NN already had one — delicate, since a record is one per
patient (Ley 26.529, art. 17), and left for later.

---

## D-61 — A patient who never came is absent, not cancelled
**Date:** 2026-09-26 · **Status:** accepted; replaces the automatic cancellation of D-06 and D-31

v1 cancelled an appointment whose patient never arrived, and said so as provisional: once
attendance was recorded, a no-show would be recorded instead. Phase 2 records it, before the queue
takes the arrival over, so the absence can be checked with the check-in it has always had.

**`ABSENT` is a state of its own.** The same sweep, every minute, now marks absent what it used to
cancel: `Appointment.markedAbsent(now)`, which refuses unless `isDueForAbsence(now)` — booked, no
check-in, and 30 minutes past the start. A late-arrival notice no longer waits 24 hours but until
the midnight that ends the appointment's day; it gives no priority. `MarkAbsentAppointmentsUseCase`
replaces `CancelOverdueAppointmentsUseCase`, and `V18` widens the status check.

**An absent appointment is a record.** It cannot be cancelled, moved, checked in or warned about:
it is what says the patient did not come, and a mistake — the patient was in the waiting room and
nobody marked the arrival — is not undone. A patient who comes after all is seen some other way:
today, a same-day appointment; from part 5, the queue, without an appointment or as an emergency.

**It frees its time**, as a cancelled one does. The overlap rules — the domain's and PostgreSQL's
exclusion constraints — cover booked appointments only, so they need no change.

**It is shown, not hidden.** The agendas and the API show absent appointments as "Ausente", with no
buttons, so reception and the practitioner see who did not come; cancelled ones still leave the
agenda. The repository's agenda reads became `findNotCancelled…`.

**An absent appointment today still opens the record.** `AppointmentReader.findNotCancelledForPatientOn`
answers booked and absent appointments, each saying whether it is absent, and the clinical record's
`AttendanceAdapter` counts an absent one as expected: the practitioner reads the record that day,
and records nothing, since nobody arrived.

**The names follow.** "Suspending the automatic cancellation" said something no longer true: it is
the late-arrival notice — `withLateArrivalNotice`, `RecordLateArrivalNoticeUseCase`,
`POST /api/appointments/{id}/late-arrival-notice` and `lateArrivalNotice` in the answer, the old
route gone. The column keeps its name, `auto_cancellation_suspended`: renaming a column is not
portable SQL.

**Before this, they stay cancelled.** Nothing tells an automatic cancellation from one by hand, so
no row is changed.

*Rejected:* undoing an absence (it is the record of what happened); keeping the 24 hours (the day
is what the patient warned about); hiding absent appointments like cancelled ones (whoever did not
come is worth seeing); keeping the old route as an alias (no client outside the application uses
it).

---

## D-62 — The arrival module keeps events, and the queues' setup is the first stream
**Date:** 2026-09-26 · **Status:** accepted

Every module is laid out by its own architecture. `arrival` — the queues — tries a third one,
built on the domain rather than on layers or on a hexagon: **event sourcing, with the commands and
the reads apart (CQRS)**. It fits what a queue is: a sequence of facts — who came, who was called,
who was seen — which part 5 will record; the log of those facts is the traceability, and part 6's
live screens will be fed by the same events.

**The module is split by subdomain**, and each subdomain by what its parts do:

```
arrival/
├── eventstore/        the event store, shared by the subdomains
└── setup/             how the queues are set up                ← part 4
    ├── domain/        Queue, its events, QueueSetupService, the refusals — no framework
    ├── command/       one use case per change: read, decide, append, project
    ├── query/         the reads, from the projection's tables
    ├── projection/    the tables the events add up to
    ├── http/  screen/
```

`session/`, each opening of a queue, arrives in part 5. The stream of a session is an opening — from
when reception opens the queue until it closes — not a day: a queue left open past midnight is one
session, and one reopened is a new one, which is why whoever was not seen does not come back.

**The setup is events too**, not a table that changes. Serving a queue will let a practitioner
read the records of the patients waiting in it, so who served which queue, and from when, is access
to clinical content, and must be traceable like the readings (D-59). As events it is, by
construction: `QueueCreated`, `QueueRenamed`, `PractitionerJoined`, `PractitionerLeft`.

**The domain.** `Queue.replay` adds a queue's events up into the queue they describe, checking each
one, so a queue read back obeys the same rules as one being set up. Deciding something answers with
the event that says so and leaves the queue as it was; applying the event gives the new one (D-17).
The decisions are package-private: the only way in is `QueueSetupService`, since two rules look
beyond one queue — no two queues share a name, whatever the case, and a practitioner serves one
queue at most. It is given the other queues' names and the practitioner's current queue, and reads
nothing. A queue needs a name up to 60 characters; its consulting room is optional, up to 20. A
queue left with nobody serving it stays; queues are never deleted.

**The event store** (`V19`, `arrival_event`) keeps, per stream and version, the event's type, its
payload as JSON text, and when and by whom it was recorded — the `Clock` and
`CurrentUserReader.username()`. The type is a fixed name chosen by the stream's owner, never a
class name, so renaming a class does not orphan what was stored. Two writers of one stream at once
take the same version, and the primary key refuses the second: `StreamChangedException`, a 409.
Plain JDBC over portable SQL, since an append-only table with a composite key gains nothing from
JPA.

**The projection guards what spans streams.** Each stream sees only itself, so the two rules across
queues cannot be kept by the events alone. `QueueProjection` writes `arrival_queue` — with a unique
key on the lower-case name — and `queue_membership` — keyed by the practitioner — in the same
transaction as the events. The service checks the rules against it; what two commands at once still
let through, the keys refuse, and the events of that transaction go with it. Being derived, both
tables could be rebuilt from the events.

**Commands and reads apart.** A command answers nothing of its own but the new queue's id; the
API runs it and reads the queue back. The reads come from the projection, never from the events,
which belong to the commands; `ArchitectureTest` checks it, and that the domain depends on no
framework, JSON library or store.

**API and screens**, for reception only: `POST /api/queues`, `GET /api/queues`, `GET` and `PUT
/api/queues/{id}`, `POST /api/queues/{id}/practitioners` and `DELETE
/api/queues/{id}/practitioners/{practitionerId}` — a `DELETE` because it takes someone out of the
queue and nothing else, while the events still say they served it. The screens `/queues` and
`/queues/{id}` offer to add only the practitioners who serve no queue yet. Nothing reads the events
themselves yet; admins will.

*Rejected:* a hexagon or layers again (the module was chosen to try something else); the setup as a
table with a history beside it (two patterns in one module, and a history written as an extra step);
the day as the stream of a queue (a queue may stay open past midnight); class names as event types
(a rename would orphan the events); JPA for the event store (nothing for it to map).

---

## D-63 — A queue at work is a session of events
**Date:** 2026-09-26 · **Status:** accepted; the patients with an appointment join it in part 5b

Part 5a puts the queues to work for the patients without an appointment, and leaves the
appointment untouched: patients with one, and the arrival leaving the appointment, are part 5b.

**A session is one opening of a queue** — from when reception opens it until it closes, by hand or
at the end of its day — and it is a stream of its own in the event store (D-62): `SessionOpened`,
`PatientArrived`, `EmergencyDeclared`, `PatientCalled`, `PatientSeen`, `PatientDidNotComeIn`,
`PatientDidNotShowUp`, `PatientLeft`, `AutoCloseTurnedOff`, `SessionClosed`. The events the order
depends on carry their time, so the domain needs nothing but the events. `QueueSession.replay` adds
them up into each patient's `Entry` — when they came, whether they are an emergency and since when,
where they stand, how often they were called, who has them — and the decisions answer with events,
through `QueueSessionService`, the only way in (D-17). It is given what a session cannot know: the
queue's open session, who serves the queue, and how many calls a patient gets.

**The order is one rule, `CallOrder`.** Emergencies first, in the order they were declared — when
they came in or while they waited — then everyone else by arrival; two at the same time keep the
order they came in. "Llamar siguiente" calls the first of that order; the practitioner never
chooses.

**Calling.** A practitioner has one patient called at a time. "Terminar" says they were seen.
"No ingresó" says they did not come in: they wait again, keep their place, and are passed over at
the next call only — unless nobody else waits. After their last call without coming in — three in
all, `app.arrival.calls-before-no-show` — they are out, as having not shown up. In a shared queue,
whoever of its practitioners calls next may call them again.

**Where a patient stands:** waiting, called, seen, left — said by reception —, not seen — still
waiting when the queue closed — or did not show up. Whoever was called when the queue closed stays
called: nobody waits any more, and their practitioner may still write their consultation that day.
Reopening starts a new session, so nobody comes back by themselves. A patient may wait in several
queues at once — queues know nothing of each other — but in each once.

**Closing by itself.** A job every minute closes the open sessions that close by themselves and
were opened on an earlier day; the events say `system` did it. Reception turns that off for the
session, for a queue that stays open past midnight.

**The projection is the session as it stands.** `SessionProjection` writes `queue_session`,
`open_queue_session` — keyed by queue, so a second opening that two requests at once could let
through is refused — and `session_entry`, in the same transaction as the events. A session is a few
dozen rows, so it is written whole after each change rather than event by event, and cannot drift
from what the events say. The reads come from it, and order the waiting ones with `CallOrder`, so
the rule stays in one place.

**The clinical record learns who a practitioner is seeing from the queue too.** `arrival/api/ArrivalReader`
answers facts — the queues a patient waits in and who serves them, and who called or saw them that
day — and `AttendanceAdapter` decides what they mean: *arrived*, so the record may be written, when
the practitioner called or saw the patient that day, or when the patient checked in on an
appointment with them; *expected*, so it may be read, when the patient waits in a queue they serve,
or has an appointment with them that day. Whoever did not come in is nobody's to write. A reading
through the queue is recorded as `VISIT_TODAY`, like one through an appointment (D-59). The
check-in on the appointment goes in part 5b.

**A patient who gives no name comes in through the queue**: `AddUnidentifiedPatientUseCase`
registers them under an alias (D-60), through the new `PatientWriter.registerUnidentified`, and
brings them in, in one transaction, so a closed queue leaves no nameless patient behind.

**API and screens.** Reception: `POST /api/queues/{id}/open`, `/close`, `/auto-close-off`, `GET
/api/queues/{id}/session`, `POST /api/queues/{id}/session/patients` and `/session/unidentified`,
`POST /api/queue-entries/{id}/emergency` and `/left`; the screen `/arrivals`. The practitioner:
`GET /api/my-queue`, `POST /api/my-queue/call-next`, `/finish` and `/did-not-come-in`; the screen
`/my-queue`, which opens the consultation of the patient called. The session's refusals carry a
reason, so the screens say them in Spanish without reading the English message.

*Rejected:* a day as a session (a queue may stay open past midnight); calling the next patient to
finish the one called (a practitioner finishes explicitly, for now); a patient who did not come in
sent to the end (they keep their place); projecting event by event (a session rewritten whole is
simpler and cannot drift); a new kind of access for the queue in the read log (it is a visit today,
like an appointment).

---

## D-64 — Patients with an appointment join the queue, kept for their practitioner
**Date:** 2026-09-26 · **Status:** accepted; the arrival leaves the appointment in the next step of part 5b

**They come into the queue of their appointment's practitioner** — the one queue that practitioner
serves — when reception says they arrived, on the appointment's day, while it is booked and the queue
is open. `AppointmentArrived` records the appointment, its time and the practitioner they are
**kept for**: only that one calls them, as an emergency too. Reception may give them to another
practitioner of the queue (`PatientReassigned`) without their place changing, or lift the
reservation (`ReservationLifted`), and then whoever serves the queue may call them. The reception
screen warns of whoever nobody can call, because their practitioner no longer serves the queue.
The events of 5a are unchanged; these are three more.

**The order has its middle tier.** `CallOrder` puts emergencies first, then patients with an
appointment, then everyone else. A patient with an appointment is placed by the later of its time and
their arrival: whoever comes early waits for their time, and whoever comes late is placed by when they
came, behind the appointments earlier than that. "Llamar siguiente" skips whoever is kept for another
practitioner. A patient kept for a practitioner is read by that practitioner only; who is kept for
nobody, by whoever serves the queue.

**A cancelled appointment leaves its patient in the queue**, in their place and kept for nobody: they
did arrive, and taking them out is reception's call. The appointment module must say so without
knowing the queues exist, so it declares what it tells — `appointment/api/AppointmentArrivals` — and
the arrival module implements it; `appointment` reaches it through its own `ArrivalPort`, like any
other port. The dependency stays one way, from the queues to the appointments, as D-48 asked. The
cancellation and the lifted reservation are kept in the same transaction.

**The late-arrival notice stays on the appointment.** It is part of the rule of absence (D-61): the
queue does not know who is coming late.

**API and screens.** `POST /api/appointment-arrivals`, `POST /api/queue-entries/{id}/reassign` and
`/lift-reservation`; a session answers who of its waiting patients nobody may call. Reception finds a
patient and, when they have an appointment today with a practitioner of the queue, "Llegó con turno";
each waiting patient shows their appointment and whom they wait for, with "Dar a" and "Quitar
reserva".

*Rejected:* an emergency with an appointment called by anyone (it stays with its practitioner, and
reception lifts that when needed); the appointment module calling the queues' api (a cycle between
modules); pulling each appointment's state when the queue is ordered (the cancellation is a fact of its
own, and kept as an event).

---

## D-65 — The arrival leaves the appointment
**Date:** 2026-09-26 · **Status:** accepted

Since v1 the check-in had lived on the appointment, and D-48 said it would stay only until the queue.
With patients with an appointment coming into the queue (D-64), the queue is where arrivals happen,
so the appointment keeps none any more: an appointment is only a booking.

**"Llegó" brings the patient into their practitioner's queue.** The agenda's button posts to the
arrival module, which brings them in and sends reception back to the agenda it was on. The queue has
to be open, and the practitioner has to serve one: the practice sets its queues up before it takes
arrivals. `POST /api/appointments/{id}/check-in` and `CheckInUseCase` are gone.

**The appointment asks the queues who came.** `appointment/api/AppointmentArrivals` — the interface
of D-64 — now also answers when the patient of each appointment arrived, and `ArrivalPort` is how
the use cases ask it. The dependency stays one way. The arrival is the first time the patient came
into a queue with the appointment, or the check-in recorded before the queues existed.

**The rules that needed the check-in are given the arrival.** `Appointment` does not know whether
its patient came, so the rules of its day that depend on it are `AppointmentDayService`'s, given the
arrival as context (D-17): a late-arrival notice makes no sense once the patient arrived, and whoever
arrived is never absent. `BookingService.reschedule` refuses to move an appointment whose patient
arrived. The appointment's own operations — the notice, being marked absent — are reached through
that service alone. The sweep reads the booked appointments that started at least 30 minutes ago,
asks the queues which of them arrived, and marks absent the rest that are due. The time-off cascade
cancels those whose patient did not arrive and reports the rest for manual handling, as before.
Cancelling by hand is untouched: the appointment does not even know the patient came.

**The agenda and the API show the arrival.** The agenda says "Llegó a las 10:05", without "Llegó",
"Avisó demora" or "Mover" for a patient who arrived; a practitioner's agenda points to "En tu cola",
where the consultation starts, since only whoever called the patient writes their record (D-63). The
API answers `arrivedAt` in place of `checkedInAt`.

**The check-ins already recorded move** (`V22`): into `appointment_check_in`, a table of the arrival
module, which answers them with the rest; `checked_in_at` leaves `appointment`. No queue session is
made up for them.

**Development data gets its queues** (`V101`, in `db/seed`): one for each seeded practitioner, set up
as the application would, with their events and projection. A database already in use may have
queues of its own, so a queue is only added for a practitioner who serves none, under a name nobody
has taken.

*Rejected:* the arrival module writing the check-in onto the appointment (the appointment would keep
the arrival, which is what this removes); the appointment calling the queues' api (a cycle between
modules); making up past sessions for the old check-ins (a queue history nobody lived).

---

## D-66 — The queue screens show themselves again as the queues change
**Date:** 2026-09-26 · **Status:** accepted; amended on 2026-09-28 (a screen whose session ran out)

A queue changes at one desk and is seen at others: reception brings a patient in and the
practitioner's screen should show them; a practitioner calls one and the waiting room should count
one fewer. The screens show the change as it happens, without anyone reloading.

**Server-Sent Events, from the server to the browser.** A screen only listens, so a one-way stream is
all it needs: `SseEmitter`, in Spring Web already, and a few lines of `EventSource` in the browser,
which opens the stream again by itself when it closes. WebSocket would be two ways for a screen that
only listens; asking every few seconds would not be as it happens.

**It says which queue changed, and nothing more.** The commands of both subdomains publish
`QueueChanged(queueId)`, an application event of the module, and `LiveQueues` sends it to every open
stream as an event named `queue` — **once the transaction commits**, with
`@TransactionalEventListener(AFTER_COMMIT)`, so a refused command tells nobody. No patient, no state:
a screen that hears it asks for itself again, through its own route and its own rules, so the stream
opens nothing a user could not already read. The streams live in memory, since the application runs
as one instance, and last half an hour before the browser opens a new one.

**A screen swaps one region.** The part that changes is marked `id="live"`, with the queue it shows in
`data-queue`; the shared `fragments :: live` script fetches the page again and swaps that region alone,
so a search reception is typing, outside it, is kept. Reception's arrivals, the practitioner's queue
and the waiting room use it. Without JavaScript, reloading does the same.

**The waiting room** (`/waiting-room`) shows every queue, its room, whether it is open, and how many
wait and are being seen — no patient by name, since anyone in the room sees it. It has no header, for a
screen on the wall. For now it is opened with reception's session on that screen; a user of its own,
who may see that screen and nothing else, comes with user administration.

**Routes.** `/queue-events` is reached by reception and the practitioners, like the agenda; the
waiting room by reception.

**Amended on 2026-09-28: a screen never goes on showing what it no longer knows.** An open stream is
not activity for the session: after half an hour with no queue changing, the session ran out while the
screen stayed open, and the next change asked the page again, got the login form, found no region to
swap and left the old one on screen, with nothing saying so — a queue shown closed that was open. Now,
when the page asked for has no `id="live"` region — the login, an error page — or the stream fails for
good, the script loads the whole page again, and the screen shows whatever that is: a login form
rather than an old queue. Found checking that the waiting room would stay open on its own, before giving it a user.

*Rejected:* WebSocket (two ways for a screen that only listens); polling (not as it happens); sending
the queue's content on the stream (the stream would need its own rules of who reads what); reloading
the whole page (what reception is typing would be lost); a public waiting room (a route without a
login, for now).

---

## D-67 — Schema migrations apply to a database that already has the seed
**Date:** 2026-09-27 · **Status:** accepted

The development data in `db/seed` is numbered from `V100`, so it stays apart from the schema's
migrations (D-34). A database started before a new schema migration existed already has `V100`
applied, and Flyway refuses by default a migration numbered below the last one applied: the
application did not start on the database in use until it was told to apply them out of order. It
was not caught earlier because every verification started from an empty database, where everything
applies in order.

**`spring.flyway.out-of-order: true`**, in `application.yml`, with the reason next to it. The seed
depends on the schema, never the other way round, so a schema migration applied after it gives the
same database as one applied before. A real deployment drops `db/seed` and this setting with it:
without the seed, the migrations always apply in order.

*Rejected:* making the seed a repeatable migration (`R__`), which Flyway runs last — tidier, but the
seed would have to be rewritten to run again and again without duplicating its rows, and a database
that has `V100` applied would need a transition; numbering the seed below the schema (the seed would
run before the tables it fills).

---

## D-68 — An admin administers the users, and nothing else
**Date:** 2026-09-28 · **Status:** accepted

Until now every user came from the development data, and the whole of reception shared one: what was
recorded under `recepcion` was nobody in particular. Every person now has a user of their own,
created by an admin. It applies D-04's postponed administration screen, in the module D-43 made for
it.

**A fourth role, `ADMIN`, with one role per user.** A user has one role and it never changes; a
person who needs two — the owner who also sees patients — has two users. By route the admin reaches
`/api/users/**` and `/users/**` and nothing else: not reception's work, and no clinical content,
which is still the practitioners' alone (D-51). On logging in each role lands on its own screen: the
admin on the users, everyone else on the agenda. The admins' screens show a header of their own.

**A user is never deleted, and a username never changes nor is used again.** What is recorded across
the application — the queues' events, the patients' identity history, the admins' own actions — names
a user by their username; deleting one, or giving their name to someone else, would put their acts
under another person. So users are deactivated and reactivated, and the username is checked against
every user there ever was.

**The user guards itself; the service guards what needs the others.** `AppUser` refuses a malformed
username — 3 to 50 lower-case letters, digits, dots, hyphens or underscores, what is typed at the
login and read in the records — and a practitioner that does not fit the role, and every change it
makes returns the entry that records it, so none can be made without one. `UserAdministrationService`
checks that the username was never used, that the practitioner exists and has no other active user —
also when reactivating —, and that an admin does not deactivate themselves, which could leave the
practice with none.

**A practitioner has one active user at most, and a user's practitioner never changes.** The admin
chooses among the practitioners with no active user. The service checks it for a clear 409; a partial
unique index in `db/vendor/postgresql` holds it when two admins pass the check at once, since only
PostgreSQL expresses "unique among the active ones" (D-44). The unique username already holds in both
engines.

**Passwords.** From 10 to 72 characters, with no rule about what they contain (`PasswordPolicy`):
length is what makes a password hard to guess. 72 is where BCrypt stops reading, counted in bytes, so
a longer one is refused rather than cut short in silence. The admin sets it when creating the user or
resetting theirs; passwords arrive in plain text and leave hashed, and no answer carries the hash.

**Every action of an admin is recorded** — created, deactivated, reactivated, password reset — with
who and when, in `app_user_change`, in the same transaction, as a patient's identity changes are
(D-60). The admins read it on each user's screen and through the API.

**A deactivated user loses access at once.** The API needs nothing: it authenticates every request,
and an inactive user is as good as unknown. The screens keep the user in the session, so every
session is registered in Spring Security's `SessionRegistry`, and the service publishes
`UserDeactivated`, an event of `user/api`, which `security` hears once the transaction commits and
answers by ending that user's sessions: their next request goes to the login form, which says why. An
event rather than a call because `security` already depends on `user/api`; `user` calling it back
would close a cycle. The registry lives in memory, since the application runs as one instance (D-66).

**Routes.** `GET` and `POST /api/users`; `GET /api/users/{id}`; `POST /api/users/{id}/deactivate`,
`/reactivate` and `/password-reset`, business operations on a sub-path (D-23); `GET
/api/users/{id}/changes`. A username taken, a practitioner taken, a self-deactivation or a user already
in that state is a 409; malformed data or an unknown practitioner a 400; an unknown user a 404 — not
the 403 of the clinical record (D-49), since the admins may see every user. The screens are `/users`
and `/users/{id}`.

**`admin` / `admin123`** is added to the development data (`V102`). How the first admin of a real
deployment is created is left for when there is one (D-34).

*Rejected:* several roles per user (the owner's two users keep what they do as admin apart from what
they do as a practitioner); deleting a user who never did anything (every username would have to be
checked against every record first, and nothing needs the row gone); changing a user's role or
practitioner (the records under their name would change meaning); rules about the password's
characters; checking on every request whether the user is still active (a query more on every
request, where ending the sessions once is enough); `admin` reaching reception's routes.

---

## D-69 — A password an admin sets is temporary
**Date:** 2026-09-28 · **Status:** accepted

The admin types the password a user starts with, and the one a reset gives them (D-68). Until the
user changes it, the admin knows it too, and whatever is recorded under the username might have been
either of them.

**Temporary until changed.** `app_user.must_change_password` (`V25`) is set when an admin creates a
user or resets their password, and cleared when the user changes it. Users already there, and those
of the development data, start without it.

**Everyone changes their own**, at `/password` — linked from every header — and `POST /api/password`,
giving the current password, and a new one that follows `PasswordPolicy` and is not the same: the
same one would leave the admin's in place. A wrong current password is a 400, not a 401, since the user
is logged in. The change is recorded in the user's history as `PASSWORD_CHANGED`, by the user
themselves.

**Until then, nothing else.** A user with a temporary password carries a `TEMPORARY_PASSWORD`
authority besides their role, and `TemporaryPasswordFilter`, after the routes are authorized, narrows
what they may reach: on the screens everything but the password, the login and logging out goes to
`/password`, which is also where they land on logging in; on the API everything but `POST
/api/password` is a 403 that says why. A filter rather than route rules, because every rule would have
to repeat "and not temporary".

**Changing it on the screen ends the session**, and the login form asks for the new password. The
session holds the user as they were when they logged in; logging in again is simpler than rewriting
it. The API authenticates every request, so it needs nothing.

*Rejected:* rewriting the session's authentication after the change (Spring Security code to save the
user one login); route rules for the temporary password; a password that expires after a while (no one
asked for it); not recording the user's own change.

---

## D-70 — The waiting room has a user of its own
**Date:** 2026-09-28 · **Status:** accepted

The screen on the waiting room's wall was opened with reception's session (D-66): anyone passing
by it held a session that reaches the whole of reception's work. It now logs in as a user of its
own.

**A fifth role, `WAITING_ROOM`,** which reaches `/waiting-room` and `/queue-events` and nothing else,
on the screens or the API; it lands on the waiting room on logging in. Reception may still open the
waiting room, as before. The admin creates such a user like any other, with a temporary password
changed on the first login (D-69); `sala` / `sala123` is in the development data (`V103`).

**Its session does not run out while nobody touches it.** Nobody does: it is a screen on the wall,
and an open stream is not activity for the session, so it would run out after half an hour without a
queue changing. On logging in, a waiting-room session is given no idle limit; every other role's runs
out as before. It holds nothing more than how many patients wait in each queue. It is still lost
when the application restarts, since sessions live in memory: the screen then shows the login form
(D-66, amended), and someone logs in again.

*Rejected:* a waiting room with no login (anyone on the network would reach it, and it may grow to
show more); a remember-me cookie that survives restarts (more to it than a restart now and then
calls for; it can come if restarts get in the way); keeping every session alive while its screen has
a stream open (reception's and the practitioners' sessions would never run out on an open screen).

---

## D-71 — The admins read who read which clinical record
**Date:** 2026-09-29 · **Status:** accepted

D-59 recorded every reading of a clinical record and left reading them to the admins, with what to
index decided then. The admins now look through them.

**What they see, and what they never see.** Each reading shows when, the practitioner and the user
they logged in as, the patient with their document, by which access, and whether what was read was
the record or an attached file. Never what was read, and never which file: a file's name is written
by the practitioner who attached it and may say what it holds. That a practitioner read a patient's
record already says something about the patient, which is why the admins alone read this, by route
(D-34): a practitioner, reception or the waiting room asking is a 403 (D-50).

**Each reading now keeps the user** — `read_by` (`V27`), the username from `CurrentPractitionerPort`,
answered by `security/api` — since a practitioner may have had more than one user (D-68). A new
reading needs it; those recorded before have none, and say so ("sin usuario registrado"), restored
through `RecordReading.restore`.

**The search.** `ListRecordReadingsUseCase` in `clinicalrecord`, which owns the readings, over
`RecordReadingReader`: by patient — the text reception types, a name, document or alias, resolved by
the patient module —, by practitioner, and by days, the last week when none are given; the most
recent first, at most 500, and the answer says when there were more so a narrower search finds them.
The use case knows ids alone; the names come from `patient/api` and `practitioner/api`, read by the
adapters (D-51). The filters apply only when given, so the JPA adapter builds the query from
specifications. `read_at`, and `patient_id` with `read_at`, are indexed.

**Routes.** `GET /api/record-readings?patient=&practitionerId=&from=&to=`, answering
`{from, to, limitReached, readings}`; a first day after the last is a 400. The screen
`/record-readings`, "Lecturas" in the admins' header.

**Looking at the readings is not recorded, for now.** The law asks that access to the clinical record
be traceable, and this shows none of it; recording who looked, and for what, is left for later.

*Rejected:* the file's name (it may be clinical); recording the admins' searches now; paging (a
narrower search does it, for a practice this size); the patient searched in `clinicalrecord` (it
would copy the patient module's search).

---

## D-72 — Refused attempts to reach clinical content are recorded
**Date:** 2026-09-29 · **Status:** accepted

D-59 recorded what is read, not what is refused: a 403 hands nothing over. But someone probing records
that are not theirs leaves nothing behind that way, so the refusals are recorded too, and the admins
read them.

**What counts as a refusal.** Two kinds:

- *The use case refuses:* a practitioner may not read a patient's record, or a file attached to it —
  `ViewClinicalRecordUseCase` and `DownloadAttachmentUseCase`, which serve the API and the screens. A
  file that does not exist is refused too, and whose it would have been is unknown. The other refusals
  of the clinical record are about writing, and are not recorded.
- *The route refuses a role:* reception, the admin or the waiting room asking for a clinical route — a
  record, a file, a consultation, a check or an order, on the API or the screens. The practitioner's
  queue is theirs but holds no clinical content, so it does not count. A request with nobody logged in
  is sent to log in, not refused, and a form refused for its missing CSRF token is refused for the
  form, not for who sent it: neither is recorded.

**What is kept:** `RecordRefusal` — who, as the username, and the practitioner they stand for, if any;
when; what they tried — `RECORD`, `ATTACHMENT` or `ROUTE`; whose record, when known; the file; and the
route, as method and path, cut at 300 characters — in `record_refusal` (`V28`), only ever inserted.
The file has no foreign key, since the one asked for may not exist.

**A refusal outlives the refusal.** Refusing throws, and throwing rolls back the use case's
transaction; so `RecordRefusalWriter` saves in a transaction of its own (`REQUIRES_NEW`), and the
refusal stays.

**The route refusals come from the security module, through an event.** The 403 of a route is written
by Spring Security, in the security module: `ApiSecurityErrorHandler` on the API, and
`ScreenAccessDeniedHandler`, which then gives the usual error page, on the screens. Both say it through
`ClinicalRouteRefusals`, which publishes `ClinicalRouteRefused(username, method, path)` in
`security/api` for a clinical route; `clinicalrecord` hears it and records it, with the patient when the
route names their record. An event, because `clinicalrecord` already depends on `security/api`, and
the other way round would close a cycle. The 403 itself does not change, and nothing tells the refused
user it was recorded.

**The admins read them** as they read the readings (D-71): who, when, what they tried, and whose record
— name and document — when known; a file said to be one, never which. By patient, by user — who may be
reception or the admin, not only a practitioner — and by days, the last week by default, 500 at most.
`GET /api/record-refusals?patient=&user=&from=&to=` and the screen `/record-refusals`, "Rechazos" in
the admins' header, theirs alone.

*Rejected:* recording the refusals in the same transaction with `noRollbackFor` (every use case that
refuses would have to remember it, and a failure after the refusal would keep it anyway); a table in
the security module for the route refusals (two places to read, and the security module keeps no
storage since D-43); recording every 403 of the application (most are reception's work refused to a
practitioner, not clinical); telling the refused user it was recorded; recording failed logins.

---

## D-73 — A patient's details: the date of birth and whose each phone and email is
**Date:** 2026-09-29 · **Status:** accepted

Until now a patient was a name, maybe a document, and how far the practice knows who they are. Reception
now also records when they were born, one phone and one email — v2, phase 4, part 1. The reminders will
need to reach the patient and web booking their email; neither is built here (D-14).

**Details, apart from the identity.** `PatientDetails` — the date of birth, the `Phone` and the `Email`,
each optional — changes through `Patient.changeDetails`, not `changeIdentity`, and has a history of its
own: changing a phone says nothing about who the patient is, and the identity's history (D-60) stays
about that. Registration takes them at once, and is not a change: nothing goes to the history then. A
patient under an alias may have them, and gets them on their screen once registered.

**Each phone and email says whose it is.** `owner` is null for the patient's own, or a `Responsible` —
a name and a `Relation`: mother, father, guardian, partner, child or someone else. Each has its own
owner, since an adolescent may have their own phone and use their mother's email, and separated parents
may give one each; the reminders will need to know whose each channel is, so as not to tell a parent of
a visit an adolescent keeps to themselves. The person is not a patient nor an entity of their own: only
a name and a relation beside the value. The phone and the email are plain columns of `patient`, not
embeddables, and come back as value objects: a nested embeddable with a nullable owner is more mapping
than it saves.

**Refused where typed, and nothing more (D-38).** A phone drops spaces, dashes, dots and brackets, and
keeps 8 to 15 digits with an optional leading `+` — the longest international number. An email is only
checked for its form and 254 characters, and kept in lower case. A date of birth is not in the future,
nor more than 130 years ago, against the practice's date from the `Clock`. An owner is a name and a
relation together, and needs its phone or email. Each value object refuses with
`InvalidPatientDetailsException`, an `IllegalArgumentException`, so the API answers 400 as for any
malformed request and the screens can say what to fix in the details rather than the identity.

**Nothing is required, a minor's responsible person included.** From 13 an adolescent decides alone on
much of their own care (Código Civil y Comercial, art. 26), and a minor who comes alone is seen (D-60):
requiring a responsible person would turn them away for the paperwork. The email is **not unique**: two
brothers may carry their mother's. Whether the email of a web account is, is decided with web booking.

**One history row per detail that changed**, in `patient_details_change` (`V29`): which detail, its
value before and after — a date as `1981-03-12`, a phone's digits, an email — and the owner apart, as a
name and a relation, so each screen says it in its own language. A save that changes nothing leaves
nothing. Reception reads it on the patient's screen and through `GET /api/patients/{id}/details-changes`.
A detail added later is one more value of `field`, and needs no new columns for its value.

**Routes.** `POST /api/patients` takes `birthDate`, `phone` and `email` —
`{number | address, owner: {name, relation} | null}` —, now through `RegisterPatientRequest`, while
`PUT /api/patients/{id}` still corrects the identity alone. `PUT /api/patients/{id}/details` sets the
details as they are to be: one left out is cleared. All of it lives under `/api/patients/**` and
`/patients/**`, reception's alone, so neither the admin nor a practitioner reaches it (D-34).

**A practitioner reads the age, never how to reach the patient.** `PatientSummary` carries the date of
birth, and the clinical record and the consultation form show the age beside the name — in years, in
months below two, as paediatrics counts it, and in days below one month. `GET /api/clinical-records/{id}`
answers `patient: {birthDate}`, asked for only once the use case granted the record, so a refused
practitioner learns nothing of the patient. The phone and the email never reach the clinical record
module.

*Rejected:* one owner for the phone and the email together (an adolescent's own phone and their mother's
email cannot both be said); the responsible person as a patient of their own (rules on their identity
and on two guardians, for nothing this phase uses); a unique email (families share one); requiring a
responsible person for minors (a minor who comes alone is seen); one history row per save with before
and after of every detail (about twenty columns, more with the health insurance); a single `PUT` for
identity and details (two histories behind one call, and a phone change would read as an identity
edit); showing the age in years alone (a baby's age is counted in months); a seed with details (the
screens give them).

---

## D-74 — A patient's health insurance, as free text, and read by practitioners too
**Date:** 2026-09-29 · **Status:** accepted

v2, phase 4, part 2. A patient has one health insurance at most: an obra social or a prepaid plan.

**What is kept.** `HealthInsurance` — the insurer's name, the member number and the plan — is one more
of the patient's details (D-73): it goes in and changes through the same `PatientDetails`, on
registering and on `PUT /api/patients/{id}/details`, as `insurance: {insurer, memberNumber, plan}`, and
lives in three columns of `patient` (`V30`). All three are free text: every insurer numbers and names
its plans its own way — PAMI's member number is fourteen digits, others carry letters —, so only the
length is checked: 100, 30 and 50 characters. The insurer's name needs a letter, and a member number or
a plan without it is refused, as half a document is (D-26). The token some insurers ask for is not
kept: it is generated anew for each visit, so it belongs to the visit, and to the cash desk when there
is one.

**None on the books is not "private".** The patients registered before this were never asked, so an
empty insurance cannot say they pay privately. The screens say "Sin obra social cargada", and whoever
pays each consultation will be the cash desk's to record.

**Its history keeps each part apart.** A change to the insurance is one more entry in
`patient_details_change`, with the insurer as its value and four columns of its own — the member number
and the plan, before and after — so the history still says what each part was. The API answers them as
`memberNumber` and `plan` beside the `value`, null for the other details.

**Practitioners read it.** A prescription and an order carry the member number, so the whole insurance
reaches the clinical record module through `PatientSummary.insurance`: the record and the consultation
form show "Obra social: OSDE · plan 310 · afiliado 61234567801", and `GET /api/clinical-records/{id}`
answers `patient.insurance`, asked for only once the record was granted, as the date of birth (D-73).
Only reception writes it; the admins read none of it.

*Rejected:* a list of insurers kept by the practice (someone has to keep it, and nothing yet counts by
insurer); more than one insurance (the cash desk will say which paid each visit); a mark for a private
patient (it would say the same as nothing for everyone never asked); the three parts joined in one
text in the history (it would no longer say which part changed); keeping the token.

---

## D-75 — A new patient is registered and booked in one transaction, and their data stays out of the address
**Date:** 2026-09-29 · **Status:** accepted

v2, phase 4, part 3: the half of D-37 that was postponed. Booking a new patient from the booking screen
registered them in one transaction and booked them in another, so a refused booking — the time was
taken — left a patient registered with no appointment.

**One use case, one transaction.** `BookNewPatientUseCase`, in `appointment`, registers the patient
through a new port, `PatientRegistrationPort`, and books them through `BookAppointmentUseCase`, which
joins its transaction: the rules of booking are the same for a new patient as for anyone. A refused
booking rolls the patient back with it, and a patient the patient module refuses books nothing.
`PatientRegistrationAdapter` answers the port with `PatientWriter`, as `PatientRegistryAdapter` answers
the one that asks whether a patient exists (D-29). The arrival queue already did the same for a
patient with no name (D-63). Its only caller, the booking screen, gives each attempt a new id, so it
does not guard against a retried id as the API's booking does (D-19): nothing could reach that path.

**The patient comes with their contact.** `PatientWriter.register` takes a `NewPatient` of the api, in
plain values: the name, the document, and a phone and an email, each with whose it is (D-73). The date
of birth and the health insurance are left to the patient's screen, so booking stays short. A phone or an
email that cannot be right is refused as `INVALID_DETAILS`, a new reason of
`PatientRegistrationRefusedException`, so the screen says what to fix in the contact; `PatientReader`
offers the relations as it offers the document types. The booking screen is the only way in: the API
books patients already registered, as since D-37.

**The new patient's data stays out of the address.** The booking screen keeps what was chosen in its
address between steps (D-35), and carried the new patient's name and document there too; with a phone
and an email that would put more personal data in the browser's history and any log of requests. Now
every step that comes back to the screen — "Actualizar", something missing, a refusal — hands the typed
patient on in a flash attribute, which lives in the session for the next request only, and the screen
fills the fields from it. The address keeps the practitioner, the day, the reason, the time and the
search.

**What the screen says changes.** A refused booking now comes back with the patient still typed in and
nobody registered, instead of "El paciente quedó registrado y elegido; falta el turno.".

*Rejected:* registering in the screen and deleting the patient if the booking fails (a patient is never
deleted, and a failure between the two would still leave one); a flag on `BookAppointmentUseCase`'s
command for a new patient (one use case with two ways in, where one calls the other); the date of birth
and the insurance on the booking screen (they can wait for the patient's screen); an API route for it
(nothing but the booking screen asks yet); keeping the new patient in the address (personal data in the
browser's history), or in a session attribute of its own (state to clear by hand, where a flash
attribute lasts one request).

---

## D-76 — Every cancellation says who, when, how and who asked, and the admins read it without the patient
**Date:** 2026-09-30 · **Status:** accepted

v2, phase 5, part 1. A cancelled appointment said only that it was cancelled: not who cancelled it,
when, why, nor whether reception or a practitioner's time off did it.

**What is recorded.** `Cancellation` — when, from the `Clock`; who, the username; how, `BY_HAND` or
`TIME_OFF`; who asked, `PATIENT` or `PRACTICE`; and reception's note, up to 200 characters, trimmed,
blank taken as none — is a value object the appointment keeps: `Appointment.cancelled(Cancellation)`
replaces `cancelled()`, and `restore` takes it back. A time off cancels on the practice's behalf, so
`TIME_OFF` with anyone else asking is refused, in the value object and in the database. Who cancelled
by a time off is whoever recorded it: nothing cancels on its own any more, since the sweep marks
appointments absent (D-61). The username comes through a new port, `CurrentUserPort`, answered by
`security/api/CurrentUserReader`, apart from `AgendaAccessPort`, which says whose agenda one reads.

**Columns of the appointment** (`V31`), not a table of their own: an appointment is cancelled once, and
a cancelled one never changes again. The four that go together are all there or all missing, and only
on a cancelled appointment; the checks say so, as D-09 asks.

**Before this, nothing.** The appointments cancelled before this phase keep no cancellation: nothing
told who cancelled them, nor whether by hand or automatically (D-61), and nothing is made up. The
patient's appointments say "Sin registro de la cancelación", and the lists, which go by the day of the
cancellation, never show them.

**Cancelling says who asked.** `POST /api/appointments/{id}/cancel` takes `{requestedBy, note}` and
answers 400 without `requestedBy`; the agenda's "Cancelar" asks "Lo pidió…" with no default, and a note.
It breaks the route's old empty body, which nothing outside the application sends (as D-61 did). The
appointment in every answer carries `cancellation`, null unless it was recorded.

**Two lists, one per role.** Reception reads the cancellations whole — `GET /api/cancellations` and
`/cancellations` —, the admins without the patient and without the note — `GET /api/cancellation-log`
and `/cancellation-log`, with their header —, to follow what the staff does. The note is reception's
alone, since it may say something close to clinical. Each route has an answer type of its own, so what
each role sees is decided by the route (D-34) and the admins' type has no place for the patient; one
use case, `ListCancellationsUseCase`, serves both. They go by the day the cancellation was made — what
the staff did that day —, showing the appointment's day beside it, filtered by practitioner, the last
week by default and at most 500, as the readings of the records (D-71). Practitioners reach neither.

*Rejected:* a table of cancellations (one per appointment, never changed, is the appointment's own);
"the application" as who cancelled by a time off (a person recorded it); one route answering by role
(what a role sees would hang on a branch, not on the route); a list of cancellation reasons (who asked
and a note say what is needed so far); listing by the appointment's day (the admins follow what was
done each day); keeping the old empty body (a cancellation would record nobody asking).

---

## D-77 — Each channel carries its own consent to be reminded, with who gave it
**Date:** 2026-10-01 · **Status:** accepted

v2, phase 5, part 2. Reminders need the patient's consent to be contacted, and an adolescent's
confidentiality hangs on who agreed to a responsible person's channel (Código Civil y Comercial,
art. 26; Ley 26.529, art. 2 d). Nothing is sent yet: part 3 reads this.

**One consent per channel, saying who gave it.** `Consent` is `PATIENT` or `OWNER` — the responsible
person the phone or the email belongs to —, and null is none. The phone and the email each have one, in
`phone_consent` and `email_consent` (`V32`): an adolescent may agree to their own phone and not to their
mother's email. The patient may consent on any of their channels; the owner only on a responsible
person's, since on the patient's own the patient is the owner. A channel that is not there takes none.
The value objects refuse it, as `InvalidPatientDetailsException`, and so do the database's checks.

**Apart from the details, and changed on its own.** `Patient.changeConsent` sets both as they are to be,
through `PUT /api/patients/{id}/contact-consent` — `{phone, email}`, one left out withdrawn — and a form of
its own on the patient's screen, under "Recordatorios", which offers the owner's consent only for a
responsible person's channel, named. `PUT /api/patients/{id}/details` does not take it: a consent is
something the patient or the owner said, not a detail reception types beside the number.

**A new number, address or owner drops it.** It was given for that one and that person, so
`changeDetails` keeps a channel's consent only when the channel stays exactly as it was — the same digits,
the same owner — and records the drop. The patients on the books, and every new one, start with none:
nobody is reminded until reception records it.

**In the history of the details.** A consent given, changed or withdrawn is one more entry of
`patient_details_change` — `PHONE_CONSENT` or `EMAIL_CONSENT`, with who gave it as the value before and
after —, read where the rest of the history is. Who reads and writes it follows the details (D-73):
reception alone; `PatientSummary` does not carry it, so practitioners never see it, and the admins reach
none of `/api/patients/**`. The API answers it as `phone.consent` and `email.consent`.

*Rejected:* one consent per patient (an adolescent may agree to one channel and not the other); the
consent inside `PUT .../details` (a save that only fixes a typo in the number would carry a consent given
for the old one, and nothing could tell it from a new one); keeping the consent when the number changes
(it was given for the old one); a consent without who gave it (part 3 has to tell an adolescent's own
agreement from a parent's); asking it on the booking screen (it can wait for the patient's screen, like
the date of birth).

---

## D-78 — Reminders are a module of their own, sent by email once a day behind a port
**Date:** 2026-10-01 · **Status:** accepted; the sender moved from `app.reminders.from` to the practice's
`app.mail.from` in D-79

v2, phase 5, part 3: the application reminds patients of their appointments by email, a few days before,
on the consent of D-77.

**A module of its own, `reminder`, laid out as a hexagon** like `appointment` (D-41): who is reminded
is a rule worth keeping pure, and the sending is an outside service. It reads the booked appointments
through `appointment/api` — `AppointmentReader.findNotCancelledBetween`, since from tomorrow on none is
absent yet (D-61) —, each patient's name, date of birth, email and consent through `patient/api` —
`PatientReader.contactsOf`, answering `PatientContact`, never the document, the phone or the insurance —,
and the practitioners' names through `practitioner/api`. No module reads it, so it has no `api/`; `ArchitectureTest` checks it like the rest. The appointment stays
only a booking (D-48): it knows nothing of reminders.

**Who is reminded is `ReminderPolicy`'s**, pure, handed what is known and the appointment's day:
- no email, `NO_EMAIL`; no consent on it, `NO_CONSENT`;
- a responsible person's email that only its owner agreed to is used for a patient under 13 on the day of
  the visit, and for nobody else: from 13 an adolescent decides alone on much of their care (CCyC,
  art. 26) and keeps their confidentiality towards their parents (Ley 26.529, art. 2 d). Without a date of
  birth the patient may be that adolescent, so it is `CONFIDENTIAL` too;
- the patient's own agreement is enough on any of their channels, a responsible person's included.

**Once a day, the next days.** `ReminderJob` runs `SendRemindersUseCase` at `app.reminders.cron`, in the
practice's time zone — 10:00 by default —, and is off with the other jobs in the tests. It considers
every appointment still booked from tomorrow to `app.reminders.days-ahead` days on — 3 by default; today's
are not reminded. One sent or failed is not tried again for that time; one moved afterwards is reminded
of its new time, since the reminder keeps the time it was about. One not sent is decided again the next
day, so a consent recorded in between counts, and each day leaves its record. A run missed — the
application was down at that time — is not made up: the next day's covers what is still ahead.

**What it says.** The practice's name, address and phone come from `app.practice.*`, the sender from
`app.reminders.from`. "Estimado/a" and the name — the patient's on their own email; the responsible
person's on theirs, naming whose appointment it is —, the day, the time and the practitioner, and "to
cancel or change it, call … or come to reception". Never the reason, which may be clinical, nor a
speciality or room the application does not know. A patient under an alias is named nowhere — `contactsOf`
answers no name for them — and greeted "Hola", which says nothing of their sex. Plain text and HTML, the
names escaped. Patients cannot cancel from it: that waits for web booking.

**Behind a port, by email.** `ReminderSenderPort` has one adapter, `EmailReminderAdapter`, over
`spring-boot-starter-mail` and `spring.mail.*`; WhatsApp or SMS would be one more, and the rules would not
change. In development the SMTP server is Mailpit, in `docker-compose.yml`, which keeps every message
for its screen and sends nothing on; the integration tests put a sender that keeps the messages in its
place. One attempt: a sending that throws is recorded as failed, and the others go on.

**What is recorded** (`reminder`, `V33`): the appointment, its patient and practitioner, the time it was
about, the channel, sent, failed or not sent and why, and when. Not the text, and not the address: a
bcrypt hash of its SHA-256 — bcrypt is salted and slow, so guessing addresses against a row is costly, and
reads only 72 bytes, which an email may pass. It answers whether a reminder went to a given address, and
nothing reads it yet. The checks keep a reason exactly on what was not sent, and a hash exactly on what
went or was tried.

**Reception reads it, by the appointment's day**: `GET /api/reminders` and `/reminders`, from today to as
far ahead as reminders go by default, a failed one marked to call the patient. The scope said "on each
appointment"; the appointment's screens are the appointment module's, which does not read this one, so
the list by the appointment's day, in its order, says it for each. The admins and practitioners reach none
of it.

*Rejected:* the reminders inside `appointment` (it is only a booking, and messaging is not its
business); SMS or WhatsApp first (a provider, a cost per message and, for WhatsApp, templates approved by
Meta, where SMTP needs none); a rolling 24 hours (a message at any hour of the day); retrying a failed
sending (a second message, or a call from reception, could cross); recording a not-sent reminder once
and for good (a consent given the next day would never count); keeping the address, or a plain SHA-256 of
it (a list of emails turns it back); reading the reminders from the appointment's screens (the
appointment module would depend on this one, which depends on it); a link to cancel (web booking).

---

## D-79 — A patient's web account: a module of its own, opened by reception and activated by email
**Date:** 2026-10-02 · **Status:** accepted

v2, phase 6, part 1. Until now only the staff used the application. A patient now has an account of their
own, to read their appointments — and, in the parts to come, to book, cancel and keep their data.

**A module of its own, `portal`, laid out as a hexagon** like `reminder` (D-78): the patients' side of
the application. Who may have an account is a rule worth keeping pure, sending the activation is an
outside service, and every later part of the phase — booking, cancelling, the family group — is something
the patient does, so it goes here too. It asks the patient module for the patient through `patient/api`,
the appointment module for the patient's appointments through `appointment/api`, and answers
`portal/api/PatientAccountReader` for the patient and security modules.

**One account per patient, opened by reception.** `AccountOpeningService`, the only way to open one (D-17),
refuses a patient who has one in any state, one whose identity is not verified — the account logs in with
their document, so reception must have seen it (D-60) —, one without a date of birth, and one under 13 on the
day: from 13 a patient decides alone on much of their care (Código Civil y Comercial, art. 26), and the
reminders already follow that age (D-78). Each refusal is a 409 with its reason. Nobody opens an account,
nor registers a patient, from the web. A second account opened at once by two receptionists is refused by the
unique patient of `patient_account` (D-09).

**The account's email is its own.** `PatientAccount` keeps an email apart from the patient's contact
(D-73): the activation goes there, and the account's later messages, never a reminder. It is checked for its
form as the contact's is, kept in lower case, and not unique — a family may share one. It changes only
through reception's `email-change`, with the patient's document in sight, so changing a contact can never
hand the account to someone else. An active account's new email holds at once; a pending one gets its
activation there, and the old link stops working.

**Activated from a link, once.** `PENDING` until the patient chooses a password, from 10 to 72 characters as
the staff's (D-68) — `PasswordPolicy` moved to `shared` so both use it —, never temporary, since nobody
else ever knew it; then `ACTIVE`; `INACTIVE` when reception switches it off, and back to `ACTIVE`, or to
`PENDING` if it was never activated. The link carries a token of 256 random bits, of which only the SHA-256
is kept, in `patient_account_activation`, one per account: for something this unguessable a hash is enough
to keep it useless if read, and lets the link find its account; the bcrypt of D-78 is for what could be
guessed. It lasts `app.portal.activation-valid-for`, 72 hours; sending another replaces it, and using it or
deactivating the account deletes it. The link's address names nothing personal (D-75). Asking for an unknown,
expired or used link is a 400 that does not say which.

**One attempt at sending.** `SendActivationUseCase` sends through `AccountMailPort`, answered by
`EmailAccountAdapter` over the same SMTP server as the reminders, in plain text: a greeting, what the account
is for, the link and when it expires. A port of its own rather than the reminders' (D-78): another address,
other words, another module. The practice's sender is one setting for both, `app.mail.from`, which replaces
`app.reminders.from`; the links point at `app.portal.base-url`. A sending that fails leaves the account
opened, says so to reception, and is not recorded; reception sends it again.

**Every action recorded**, in `patient_account_change`: opened, activation sent, activated, email changed,
deactivated, reactivated — when, and by whom: reception's username, or nobody for what the patient did,
which the database checks. Reception reads it on the account's screen and at
`GET /api/patient-accounts/{id}/changes`.

**A deactivated account loses access at once.** `PatientAccountDeactivated`, in `portal/api`, is heard by
the security module once it commits, which ends the account's sessions as it ends a deactivated user's
(D-68): an event because the security module already depends on `portal/api`.

**A patient with an account keeps a verified identity.** If reception could change their name or document
without seeing it, the account would log in with a document nobody saw. So `Patient.changeIdentity` refuses,
as `VerificationRequiredException` — a 409 —, any change that would leave a patient with an account, in any
state, unverified: a new name or document goes in only with the document seen. The patient module asks
`portal/api` whether the patient has an account; `PatientAccountReaderAdapter` reaches nothing of the patient
module, so the two depend on each other's api without a cycle between their beans.

**Reception's routes and screen.** `POST /api/patient-accounts`, `GET /api/patient-accounts?patientId=` and
`/{id}`, `POST /{id}/activation-resend`, `/email-change`, `/deactivate`, `/reactivate`, `GET /{id}/changes`,
reception's alone by route (D-34); an unknown patient in the body is a 400, as an unknown practitioner is in
D-68. The screen `/patient-accounts?patient=` belongs to `portal` and is reached by a plain link, *Cuenta
web*, from the patient's screen, as the reminders' list is (D-78). The admins read none of it.

**Development data.** Laura Medina, verified and adult, with an account in use: `DNI 27333444` /
`paciente123` (`V104`).

*Rejected:* opening an account from the web with the document and the email on file (anyone holding a
patient's document could try, and a parent's email on an adolescent's record would open it to the parent);
using the contact email for the account (reception changing a contact would hand over the account); a unique
email (a family shares one); the account in the patient module (the patients' side has rules and outside
services of its own, and every later part of the phase belongs with it); the reminders' sending port for the
activation (another module's words and address); bcrypt for the token (it is unguessable, and a link must
find its account); keeping the account usable when the identity is no longer verified, or checking that at
every login (simpler and safer for it never to happen); a seed account without its patient (the rules of
opening would be skipped for nothing).

---

## D-80 — Patients log in with their document, on a login, a session and routes of their own
**Date:** 2026-10-02 · **Status:** accepted; the principal's name became `portal:` and the account's id in D-81

v2, phase 6, part 1. The staff log in with a username; a patient has none, and the account requires their
identity document and **never** the internal id (the context's planned evolution).

**Two more filter chains, ahead of the staff's.** `/api/portal/**`, with basic authentication and no
session, the contract of the patients' single-page application to come (D-02); and `/portal/**`, with a
login form at `/portal/login`, a session and CSRF. A patient carries one authority, `ROLE_PATIENT`, which no
staff route accepts, and the patients' routes accept no other: a patient reaches nothing of the staff's,
nor the staff anything of theirs, as tests check both ways. Each chain names whom it looks up —
`AppUserDetailsService` or `PatientAccountDetailsService` —, since there are two kinds of people to log in.
Activating an account, before there is one to log in with, is open: the link is what says whose.

**The document is the login.** `PatientAccountDetailsService` reads it as its type and number with a space,
`DNI 27333444`; the patient module finds the patient — separators and case as reception types them
(D-26) — and the portal their account, if it is in use. Two lookups in two modules, because the patient
module asks the portal whether a patient has an account (D-79), and the portal asking back would close a
cycle. On the screen the type is chosen from a list and the number typed apart; `DocumentLoginFilter`
joins them before Spring Security's form login reads its single username, on the login's request alone.
Without JavaScript nothing else could join them.

**The principal is named by the account's id, never the document.** Whatever records who did something
reads the principal's name (D-60), and a document there would tell anyone reading a record who the patient
is — the admins included, who read the cancellations without the patient (D-76). `CurrentPatientReader`, in
`security/api`, says which patient is logged in; the use cases never take a patient from the request, so a
patient cannot ask for another's.

**A session of their own.** The patients' session keeps its login under a key of its own,
`PORTAL_SECURITY_CONTEXT`, so the same browser logged in as staff and as a patient keeps both, and neither
is taken for the other. The sessions are registered as the staff's, so a deactivated account loses its own
at once (D-79) — but in a registry of their own, `patientSessionRegistry`: a registry keeps one login per
session, and with one for both, someone of the staff logging in on the patient's browser replaced the
patient's entry, and deactivating the account found no session to end. The browser showed it; MockMvc cannot,
since it changes a session's id without telling the registries, as the servlet container does. Ending that
session ends the whole browser's session, the staff's login in it included, and so does logging out.

**What a patient reads: their appointments.** `GET /api/portal/appointments` and the screen
`/portal/appointments`, *Mis turnos*: those ahead, the soonest first, and those of the last 12 months, the most
recent first (`OwnAppointments`), each with its day and time, practitioner, reason and state — booked,
cancelled or absent — and, cancelled, whether the patient or the practice asked. Never reception's note, nor
who cancelled it: `appointment/api/PatientAppointment`, which `AppointmentReader.findForPatient` answers, has
no place for them, so the type decides it, as the admins' list did in D-76. Nothing clinical (D-51).

**The patients' screens** are in Spanish, with a header of their own, *Mis turnos* and *Salir*, and as thin
and throwaway as the staff's (D-02).

*Rejected:* one login for staff and patients, with a `PATIENT` role in `app_user` (users are the staff's,
created by the admin, with usernames never used again — D-68); logging in with the email (it is not unique)
or the internal id (the context forbids it); the document as the principal's name (it would reach every
record of who did what); a single field for the whole document (patients would have to know how to type
it); a custom authentication filter for the form (Spring Security's form login does the rest, once the
username is joined); the staff's session key for the patients (logging in on one side would log out the
other).

---

## D-81 — A patient changes their own contact, consent and health insurance, with reception's rules
**Date:** 2026-10-02 · **Status:** accepted

v2, phase 6, part 2. Until now only reception changed how to reach a patient and their health insurance. The
patient now changes them from their account; their name, document and date of birth stay reception's, since
they are what verifying the identity looked at.

**The rules stay in the patient module.** `PatientWriter` gains `changeContact` and `changeOwnConsent`, and
`PatientReader` `profileOf`, which answers `PatientProfile`: name, document, date of birth, the phone and the
email with whose each is and its consent, and the insurance — nothing clinical, nothing of reception's own.
`Patient.changeContact` is `changeDetails` with the date of birth as it was, so a new number, address or owner
still drops its consent (D-77) and every change still goes to the history; a value that cannot be right is
refused as `PatientChangeRefusedException`, which the portal turns into a 400. The portal repeats none of it:
its use cases take the patient from the login and ask through `PatientDataPort`.

**Consent from the web is the patient's.** On each channel, yes records the patient's own consent, no
withdraws any — a responsible person's too, since the reminders are about the patient's appointments —, and
a channel left out stays as it was (`Patient.changeOwnConsent`). Every account holder is 13 or older, so a
responsible person's consent alone never reminds them (D-78): the patient's own is what counts. The screen
offers, for each channel, the consent that stands first — "No" when nobody agreed, so saving the form
untouched never agrees —, and keeping the owner's only while it stands. As at reception, the consent is
changed apart from the contact (D-77).

**Who changed it is the account, never the document.** A patient's principal goes by `PatientUsername`,
`portal:` and the account's id, wherever something records who did it: a staff username cannot hold a colon
(D-68), so the two are never confused, and the patient's screen of reception says "el paciente (web)" where
the API answers the name as it is. The cancellation log will read it the same way (part 4).

**Routes and screen.** `GET /api/portal/profile`; `PUT /api/portal/profile/details`, `{phone, email,
insurance}`, one left out cleared, shaped as reception's; `PUT /api/portal/profile/contact-consent`,
`{phone, email}` as true, false or left out. The screen `/portal/profile`, *Mis datos* in the patients'
header, with a form for each.

*Rejected:* the patient changing their date of birth (verifying the identity looked at it, and a wrong one
would change who is reminded, D-78); the portal checking the formats itself (two places for one rule); one
route for the contact and the consent (a typo fixed would carry a consent given for the old value, D-77); a
consent left out withdrawn, as at reception (a patient touching only their phone would drop their mother's
consent on the email); the account's bare id as who changed it (nothing would tell it from a staff username);
reading the history from the web (reception's, as before).

---

## D-82 — A patient books on the web what reception opens to it, three ahead at most
**Date:** 2026-10-02 · **Status:** accepted

v2, phase 6, part 3. Until now only reception booked. A patient now books an appointment for themselves
from their account.

**Reception opens each reason to the web.** `reason.web_bookable` (`V35`), false at first, so nothing is
offered until reception says so: on the practitioners' screen, "Se reserva por la web" beside each reason, and
`webBookable` in the reason's `PUT`, false when left out. A new reason is never booked on the web; reception
marks it afterwards. `ReasonDetails` carries it to the other modules.

**What the web books is the portal's; how any appointment is booked stays the appointment module's.**
`WebBookingPolicy`, pure, is handed what it needs: a reason opened to the web; a day from tomorrow to
`app.portal.booking-days-ahead` days on, 30 — today's appointments are reception's —; one of the times the
agenda offers that day, every quarter of an hour where a slot of its length is free (D-33), not any free time
as reception may; and fewer than three appointments ahead, every booked one counted whoever booked it — a
limit of the web's, which reception books beyond. Then the appointment module books it through
`appointment/api/AppointmentWriter.bookOnWeb`, with `BookAppointmentUseCase` and every rule reception's
bookings follow: the practitioner's hours, no overlap with their agenda nor with the patient's. Its refusals
come back as `BookingRefusedException`, of the api, which the portal tells the patient as a time taken or
another appointment of theirs at that time. `AppointmentReader.suggestedTimes` answers the times offered,
with `SuggestSlotsUseCase`. Two bookings of one patient at the very same moment could both pass the limit; for
a small practice that is accepted, not guarded by the database.

**The appointment keeps where it was booked.** `Appointment.bookedVia`, `RECEPTION` or `WEB`, given when it is
booked and never changed afterwards; `appointment.booked_via` (`V35`), the appointments already there booked
at reception, the only place there was. The agenda and the patient's appointments mark the web's "· Web", and
the API answers `bookedVia`. Who booked it, beyond where, is not recorded (the context). A web booking has no
note.

**The confirmation goes to the account that booked it,** in plain text: the practice, the day, the time and
the practitioner, and how to cancel or change it — by calling or at reception, until cancelling from the web
comes (part 4). Never the reason, as the reminders (D-78). One attempt: a confirmation that does not go out
leaves the appointment booked, and the patient is told. `AccountMailPort` sends it beside the activation.

**Routes and screen.** `GET /api/portal/booking/options` — the days and each practitioner with the reasons
booked on the web —; `GET /api/portal/booking/times?practitionerId=&reasonId=&date=`; `POST
/api/portal/appointments`, `{practitionerId, reasonId, startsAt}`, 201 with whether the confirmation went
out. What the web does not book is a 409, as a refusal for the state (D-23). The screen `/portal/booking`,
*Sacar turno* in the patients' header: a reason with its practitioner and a day, then the times offered,
each a button that books it.

*Rejected:* every reason on the web (reception decides what may be booked without a word with the patient);
any free time, as reception books (the agenda would fill with odd times); a limit per account or per family
(the patient is who has the appointments); a unique constraint against two bookings at once (a counted
limit is not a constraint, and the race is a patient booking twice in the same instant); the reason in the
confirmation (it may be clinical); recording who booked each appointment (not asked, beyond where); same-day
booking on the web (today's agenda is reception's to manage).

---

## D-83 — A patient cancels up to a day before, on the web or from an email's link, and the log says so
**Date:** 2026-10-02 · **Status:** accepted

v2, phase 6, part 4. Until now only reception cancelled, and the reminder told the patient to call (D-78).
A patient now cancels from their account, and anyone who got a reminder or a booking's confirmation cancels
from the link in it.

**Up to a day before.** `WebCancellationPolicy`: a booked appointment of the patient's — reception's bookings
too — up to `app.portal.cancel-hours-before` hours before it, 24. Later, they call; reception still cancels
it, as it always could. Moving an appointment is not offered: they cancel and book again. A patient asking
to cancel someone else's appointment is refused with a 403 (D-50).

**From an email's link, with nobody logged in.** `CancelLink`, in `portal`: a `SecretToken` — the same
256 random bits as the activation's, now one class for both —, of which only the SHA-256 is kept in
`cancel_link` (`V36`), with the appointment and the time it was about. It has no expiry of its own: it works
while its appointment is still booked at that time and while there is time to cancel it, so moving the
appointment ends it, and its new reminder brings a new link. One is issued for each reminder and for each
web booking's confirmation; none when there is no time left, and the email says to call instead. The page,
`/portal/cancel?token=`, shows the day, the time and the practitioner — what the email already says —, not
the patient's name nor the reason, since an email may be forwarded, and a button. The reminder module asks
for the link through `portal/api/CancelLinkWriter`, which answers its address under `app.portal.base-url`: a
new dependency of `reminder` on `portal`'s api, and none back.

**The cancellation log says the patient asked, and how** (D-76). `CancellationOrigin` gains `ONLINE` — by the
account, whose name is `portal:` and its id (D-81) — and `EMAIL_LINK`, made by `Cancellation.EMAIL_LINK_BY`,
since nobody can tell who held the email: a child's mother may cancel it from the reminder she got. Either
is asked by the patient, which the value object and `V36`'s check hold, and has no note. Reception reads "el
paciente (web)" or "el link del email", "Por la web" or "Con el link del email"; the admins read "paciente
(web)" for both, and the admins' API answers no `cancelledBy` for them, since the account would lead back to
the patient. The appointment module cancels through `AppointmentWriter.cancelOnWeb` and
`cancelFromEmailLink`, with `CancelForPatientUseCase`; the arrivals are not told, as they are of reception's
cancellations (D-64), since a patient cancels a day ahead at least and cannot be waiting with it.

**What the emails say.** The reminder, in the practice's usted: "Para cancelarlo, entre a este link", then
"Para cambiarlo, llame al … o acérquese a recepción"; without a link, "Para cancelarlo o cambiarlo, llame…", as
before. The confirmation of a web booking alike, in the voseo of the patients' screens. There is no confirming
an appointment: nothing would read it.

**Routes and screens.** `POST /api/portal/appointments/{id}/cancel`; `GET /api/portal/cancel-links/{token}` and
`POST /api/portal/cancel-links/{token}/cancel`, open to anyone, a link leading nowhere a 404 and one too late a
409. *Mis turnos* offers "Cancelar" on the appointments that may still be cancelled.

*Rejected:* a link that requires logging in (most patients reminded have no account); the patient's name on
the link's page (the link travels with the email, and may be forwarded); the link's own expiry date (the
appointment's state and time already say when it stops working); a confirmation of attendance (nothing reads
it); moving an appointment on the web (cancel and book again); the patient's document or account as who
cancelled for the admins (it would lead back to the patient); telling the arrivals (unreachable a day ahead).

## D-84 — Reception forms family groups, with each member's age deciding how they join and whether they share
**Date:** 2026-10-03 · **Status:** accepted

v2, phase 6, part 5a. So that a family books from one account (part 5b), reception first forms the group at
the desk. What a member then does for the others on the web is part 5b; this part only forms the groups and
keeps their history.

**In `portal`, as its own aggregate.** `FamilyGroup`: a name reception gives it — "Familia Pérez", up to 100
characters — and its members, `FamilyMember`: the patient, whether they share their appointments with the
group, and for a child who asked for them to be added and what they are to the child, `MOTHER`, `FATHER` or
`GUARDIAN`. A patient may be in several groups, and is in each once. It belongs to `portal` because only the
patients' web reads it; the patients are the patient module's, read through `PatientReader` and the
`AccountHolderPort` the accounts already use, and `family_member` points at them without a foreign key (D-30).

**The ages decide, and `FamilyGroupService` is the only way in** (D-17). `AgeBand`: a child under 13, an
adolescent from 13 to 17, an adult from 18, counted on the birthday. Someone of 13 or older joins by
themselves, present and agreeing: an adult's agreeing is sharing their appointments; an adolescent's stay
private unless they choose to share them (Código Civil y Comercial, art. 26; Ley 26.529, art. 2 d). A child
under 13 is added at the request of an adult of the group, with the relation named and once reception has
seen what proves it — the form's checkbox is what records that it did —; they share always, since their
parents act for them. From 13 each member chooses whether to share, and reception changes it at their
request. Nobody joins without a date of birth, since nothing could tell which of these applies; one taken
away later leaves the member as they were, read as "sin fecha de nacimiento".

**A child's sharing turns private on their thirteenth birthday, with nobody doing anything.** A child is
stored with `shares` false, and `FamilyGroupService.sharesOn` reads a child's as true while they are under
13; on the birthday the stored value — their own choice, which nobody has made yet — is what counts. No job
runs on birthdays.

**Every change is recorded.** `family_group_change`: the group's creation, a member joining or added as a
child, removed, starting or stopping to share — with who, reception's username, and when. Removing a member
removes them alone: a parent leaving leaves the children in the group, with whoever else is in it.

**Routes and screens, reception's** (D-34). `POST /api/family-groups`, `GET` them — all by name, or a
patient's with `?patientId=` — and one by id; `POST /api/family-groups/{id}/members` (`patientId`, `shares`),
`…/children` (`patientId`, `requestedBy`, `relation`, `proofSeen`), `…/members/{patientId}/remove` and
`…/members/{patientId}/sharing` (`shares`); `GET …/changes`, the most recent first. A rule refusing is a
409, a group that is not there a 404, a patient who is not a 400. The screens, `/family-groups` and
`/family-groups/{id}`, reached from the patient's: the members with their age and whether the group sees
their appointments — "Siempre (menor de 13)", "Comparte sus turnos", "Turnos privados" —, who asked for a
child, a search to add patients, a button to share or stop, and the history.

*Rejected:* a household or address as the group (a separated parent's child is in two); the parent-child
link stored on the patient (it is only the group's, and only for asking to add a child); recording the
document seen (reception's checkbox records that it was seen, as with the identity, D-60); a job on
birthdays (the age is computed when read); a child joining by themselves, or an adolescent added by a
parent (each joins as their age allows); a member's appointments shared by default from 13 (private until
they say so); patients forming groups on the web (reception sees the proof).
