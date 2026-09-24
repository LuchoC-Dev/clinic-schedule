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
**Date:** 2026-09-22 · **Status:** applied on 2026-09-23 (see D-31)

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
**Date:** 2026-09-23 · **Status:** accepted

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
**Date:** 2026-09-23 · **Status:** accepted

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
**Date:** 2026-09-23 · **Status:** accepted; "no JavaScript" relaxed by D-36

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
**Date:** 2026-09-23 · **Status:** accepted; a practitioner asking for another agenda is refused since D-39

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
**Date:** 2026-09-23 · **Status:** accepted

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
**Date:** 2026-09-23 · **Status:** accepted

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
**Date:** 2026-09-23 · **Status:** accepted

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
**Date:** 2026-09-23 · **Status:** accepted

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
would make `AgendaReader` public for no gain. `patient` and `practitioner` are not hexagonal and
stay flat (D-25), since splitting them would make their package-private classes public.

The earlier decisions keep the paths they were written with; `appointment/external/` in D-29 and
D-30, for example, is now `appointment/adapter/out/external/`.

*Rejected:* dividing by file count (the number says nothing about where a file belongs);
`adapter/in/http/rest/` (a level with nothing to tell apart); an `api/` package for the REST
controllers (in this repository `api/` is what a module exposes to the others, D-05).

