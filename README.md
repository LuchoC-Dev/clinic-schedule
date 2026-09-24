# Clinic appointment schedule

An internal appointment schedule for a small medical practice — three or four practitioners
and one reception desk. Reception books, moves and cancels appointments, checks patients in,
and records the practitioners' working hours and time off. Each practitioner reads only their
own agenda.

It was built in stages, adding nothing before the stage that used it. The functional scope is
in [`docs/context.md`](docs/context.md), every technical decision and its reasoning in
[`docs/decisions.md`](docs/decisions.md), and the progress in [`docs/build.md`](docs/build.md).
**Version 1 is complete.**

## Running it

- **JDK 21.** On this machine it is at `C:\Program Files\Java\jdk-21`, while the `java` on the
  `PATH` is 25, so point `JAVA_HOME` at 21 first:

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
  ```

- Maven does not need to be installed: the repository ships the wrapper.

```powershell
.\mvnw test                # the whole suite
.\mvnw spring-boot:run     # the application, on http://localhost:8080
```

The database is in-memory H2 in PostgreSQL mode, created by Flyway on every start and lost when
the application stops. It starts with development data: two practitioners with their reasons
and weeks, two patients, and these users.

| User        | Password       | Role                                       |
|-------------|----------------|--------------------------------------------|
| `recepcion` | `recepcion123` | Reception: everything                      |
| `arias`     | `arias123`     | Practitioner: reads only Dra. Arias's agenda |
| `benitez`   | `benitez123`   | Practitioner: reads only Dr. Benítez's agenda |

They live in `src/main/resources/db/seed`, a Flyway location of its own that a real deployment
removes (D-34).

## Using it

**Screens** — open http://localhost:8080 and log in. Reception gets the agenda — a day or a
week, of one practitioner or everyone — with the day's operations as buttons (*Llegó*,
*Avisó demora*, *Mover*, *Cancelar*), a booking screen that suggests the free times, a patients
screen, and each patient's appointments, cancelled ones included. A practitioner gets their own
agenda, read-only. The screens are in Spanish, for the staff.

**API** — basic authentication on every request. Errors share one body:
`{timestamp, status, error, message, fields?}`.

| Method | Route | What it does |
|---|---|---|
| `POST` | `/api/patients` | register a patient, with an optional typed document |
| `PUT` | `/api/patients/{id}` | correct a name, add or remove the document |
| `GET` | `/api/patients/{id}`, `/api/patients?query=` | find patients by name or document |
| `POST` | `/api/practitioners` | register a practitioner |
| `PUT` | `/api/practitioners/{id}` | rename |
| `GET` | `/api/practitioners`, `/api/practitioners/{id}` | with reasons, week and time off |
| `POST` | `/api/practitioners/{id}/reasons` | add a reason with its duration |
| `PUT` | `/api/practitioners/{id}/reasons/{reasonId}` | rename it or change its duration |
| `PUT` | `/api/practitioners/{id}/weekly-schedule` | replace the week; a break is a gap between stretches |
| `POST` | `/api/appointments` | book; the duration follows from the reason |
| `POST` | `/api/appointments/{id}/reschedule` | move it, keeping its duration |
| `POST` | `/api/appointments/{id}/cancel` | cancel it, keeping the record |
| `POST` | `/api/appointments/{id}/check-in` | the patient arrived |
| `POST` | `/api/appointments/{id}/suspend-auto-cancellation` | the patient warned they will be late |
| `GET` | `/api/appointments?practitionerId=&date=` | a day's agenda — a practitioner may only ask for their own |
| `POST` | `/api/time-off` | a practitioner's absence, cancelling the appointments it hits |

A booking without a check-in is cancelled automatically 30 minutes after its start, or 24 hours
after it when the patient warned they would be late.

## How it is built

```
src/main/java/com/clinic/schedule/
├── appointment/              hexagonal: the only module with rules of its own
│   ├── domain/               plain Java — Appointment, BookingService, TimeSlot…
│   │   └── exception/        the rules' refusals
│   ├── application/
│   │   ├── usecase/          one class per operation
│   │   ├── port/             what the module needs from outside
│   │   └── exception/        the use cases' refusals
│   └── adapter/
│       ├── in/               driving: whoever calls the use cases
│       │   ├── http/         the REST API
│       │   ├── screen/       the Thymeleaf screens
│       │   └── scheduling/   the job that cancels overdue appointments
│       └── out/              driven: the ports' implementations
│           ├── persistence/  JPA
│           └── external/     the other modules, through their api/
├── patient/                  layered: presentation/ (http, screen), service/, entity/,
│                             persistence/ — and api/, its only door for other modules
├── practitioner/             layered alike, with the week's rules in plain classes
├── security/                 layered alike: users, login, roles
└── shared/                   the clock; http/ holds the API's error body
```

A module reaches another only through its `api/` package, and in the layered modules each layer
is reached only by the one above it; `ArchitectureTest` checks both on every build (D-05, D-42).
The domain of `appointment` has no framework annotations and is tested without Spring. The whole
repository is in English; on-screen text is in Spanish (D-13).

## Deliberately postponed

PostgreSQL with `docker-compose`, which also makes the overlap check safe when two
receptionists book the same slot at once (D-21); and a screen for practitioners' configuration and time off, which today is API-only. The
planned evolution after v1 — patient file, arrival queue, web booking — is in the context.
