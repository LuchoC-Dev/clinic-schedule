# Clinic appointment schedule

An internal appointment schedule for a small medical practice — three or four practitioners
and one reception desk. Reception books, moves and cancels appointments, checks patients in,
and records the practitioners' working hours and time off. Each practitioner reads their own
agenda, and records what happened in each consultation in the patients' clinical records.

It was built in stages, adding nothing before the stage that used it. The functional scope is
in [`docs/context.md`](docs/context.md), every technical decision and its reasoning in
[`docs/decisions.md`](docs/decisions.md), and the progress in [`docs/build.md`](docs/build.md).
**Version 1 is complete. Version 2 is built in phases; the first, the clinical record, is
closed.**

## Running it

- **JDK 21.** On this machine it is at `C:\Program Files\Java\jdk-21`, while the `java` on the
  `PATH` is 25, so point `JAVA_HOME` at 21 first:

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
  ```

- Maven does not need to be installed: the repository ships the wrapper.
- **Docker**, for the database.

```powershell
docker compose up -d        # PostgreSQL 18, with its data kept in a volume
.\mvnw spring-boot:run      # the application, on http://localhost:8080
.\mvnw test                 # unit tests: nothing needed, no database
.\mvnw verify               # unit and integration tests: H2 in memory, no Docker needed;
                            # the PostgreSQL tests run too when Docker is up, and are skipped otherwise
```

Flyway creates the schema on the first start, and the data stays between restarts;
`docker compose down -v` wipes it. It starts with development data: two practitioners with their
reasons and weeks, two patients, and these users.

| User        | Password       | Role                                       |
|-------------|----------------|--------------------------------------------|
| `recepcion` | `recepcion123` | Reception: everything                      |
| `arias`     | `arias123`     | Practitioner: Dra. Arias's agenda and consultations |
| `benitez`   | `benitez123`   | Practitioner: Dr. Benítez's agenda and consultations |

They live in `src/main/resources/db/seed`, a Flyway location of its own that a real deployment
removes (D-34).

## Using it

**Screens** — open http://localhost:8080 and log in. Reception gets the agenda — a day or a
week, of one practitioner or everyone — with the day's operations as buttons (*Llegó*,
*Avisó demora*, *Mover*, *Cancelar*), a booking screen that suggests the free times, a patients
screen — where a patient who gives no name is registered under an alias, and each patient's
identity is corrected and verified, with its history — each patient's appointments, cancelled ones included, a practitioners screen to set
up their reasons and week and record their absences, a queues screen to set up the arrival
queues and who serves each, and the arrivals screen, where queues are opened and closed and
patients brought in. A practitioner gets their own agenda,
read-only, with today's patients linked to their clinical record and *Empezar consulta* once
they arrived: a form beside the record, sent once and never changed, and completed afterwards
with addenda. Under *Mi cola* they call the next patient of their queue and start their consultation. Under *Resultados pendientes* they find the checks waiting for a result, and ask
for access to a record to report one; under *Mis órdenes*, they follow up the orders they issued. The screens are in
Spanish, for the staff.

**API** — basic authentication on every request. Errors share one body:
`{timestamp, status, error, message, fields?}`.

| Method | Route | What it does |
|---|---|---|
| `POST` | `/api/patients` | register a patient, with an optional typed document; `documentSeen` verifies it at once |
| `POST` | `/api/patients/unidentified` | register a patient who gives no name, under an alias such as NN-4821 |
| `PUT` | `/api/patients/{id}` | correct a name, add or remove the document; `documentSeen` verifies the identity |
| `GET` | `/api/patients/{id}`, `/api/patients?query=` | find patients by name, document or alias |
| `GET` | `/api/patients/{id}/identity-changes` | the history of a patient's identity: what changed, when and by whom |
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
| `POST` | `/api/appointments/{id}/late-arrival-notice` | the patient warned they will be late |
| `GET` | `/api/appointments?practitionerId=&date=` | a day's agenda — a practitioner may only ask for their own |
| `POST` | `/api/time-off` | a practitioner's absence — whole days up to `untilDate`, or part of one — cancelling the appointments it hits |
| `POST`, `GET` | `/api/queues` | set up a queue — a name nobody else has, an optional room — and list them with who serves each |
| `GET`, `PUT` | `/api/queues/{id}` | read a queue, rename it or change its room |
| `POST` | `/api/queues/{id}/practitioners` | a practitioner starts serving the queue; one queue at most each |
| `DELETE` | `/api/queues/{id}/practitioners/{practitionerId}` | a practitioner stops serving it; the queue stays |
| `POST` | `/api/queues/{id}/open`, `/close`, `/auto-close-off` | open a queue — a new session —, close it, keep it open past its day |
| `GET` | `/api/queues/{id}/session` | the open session: who waits, in the order they will be called, who was called, who is done |
| `POST` | `/api/queues/{id}/session/patients`, `/session/unidentified` | bring a patient in, or one who gives no name, as an emergency or not |
| `POST` | `/api/queue-entries/{id}/emergency`, `/left` | make a waiting patient an emergency, or say they left |
| `POST` | `/api/appointment-arrivals` | a patient with an appointment today arrives: into their practitioner's queue, kept for them |
| `POST` | `/api/queue-entries/{id}/reassign`, `/lift-reservation` | give a waiting patient to another practitioner of the queue, or to anyone |
| `GET` | `/api/my-queue` | a practitioner's own queue |
| `POST` | `/api/my-queue/call-next`, `/finish`, `/did-not-come-in` | call the next patient, finish with them, or say they did not come in |
| `POST` | `/api/consultations` | a practitioner records the consultation of a patient who arrived today — notes, prescriptions, the checks done and the orders issued |
| `POST` | `/api/consultations/{id}/addenda` | an addendum to a consultation already sent, by any practitioner with access to the record |
| `POST` | `/api/checks` | a check without a consultation — the emergency precaution — with its result or pending; it may fulfil an open order |
| `POST` | `/api/checks/{id}/addenda` | an addendum to a check — how a wrong result is corrected — by any practitioner with access to the record |
| `POST` | `/api/orders/{id}/cancel` | the author cancels an open order, with a reason |
| `POST` | `/api/consultations/{id}/attachments`, `/api/checks/{id}/attachments` | attach a PDF, PNG or JPEG of up to 10 MB, as `multipart/form-data` with a `file` part |
| `GET` | `/api/attachments/{id}` | download an attached file — only for whoever may read the record |
| `GET` | `/api/orders/mine` | the user's own orders not cancelled, and where each stands — no result texts |
| `POST` | `/api/checks/{id}/result` | the result of a pending check, by any practitioner with access to the record |
| `GET` | `/api/checks/pending` | the checks of the practice waiting for a result: only the patient and the check |
| `POST` | `/api/checks/{id}/access-grants` | access to the record to report that pending check — granted at once, for now, and recorded |
| `GET` | `/api/clinical-records/{patientId}` | a patient's consultations with their checks, and the checks done without one — for a practitioner who saw them within the access period or is seeing them today |

A booking without a check-in is marked absent 30 minutes after its start, or at the end of its
day when the patient warned they would be late; an absent appointment is kept, shown on the agenda
and changed no more. The consultation routes are the
practitioners' only; anything a user may not see answers 403. The access period to a clinical
record is `app.clinical-record.access-period`, a year by default.

## How it is built

```
src/main/java/com/clinic/schedule/
├── appointment/              hexagonal: the bookings and their rules
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
├── clinicalrecord/           hexagonal too: consultations, checks, orders, attachments, and who may read a record
├── patient/                  layered: presentation/ (http, screen), service/, entity/,
│                             persistence/ — and api/, its only door for other modules
├── practitioner/             layered alike, with the week's rules in plain classes
├── security/                 the access rules: login, roles per route, who is asking
├── user/                     layered alike: the users, and the role each one has
└── shared/                   the clock; http/ holds the API's error body
```

A module reaches another only through its `api/` package, and in the layered modules each layer
is reached only by the one above it; `ArchitectureTest` checks both on every build (D-05, D-42).
The domains of `appointment` and `clinicalrecord` have no framework annotations and are tested
without Spring. The whole repository is in English; on-screen text is in Spanish (D-13).

## Deliberately postponed

User administration; registering a new patient and booking them in one transaction; from the
clinical record, marking results as reviewed, orders and files coming from elsewhere, and moving
the files to an object store. The next phases — the arrival queue, web booking and the rest — are
in the context's planned evolution.
