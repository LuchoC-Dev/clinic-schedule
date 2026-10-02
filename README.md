# Clinic appointment schedule

An internal appointment schedule for a small medical practice — three or four practitioners
and one reception desk. Reception books, moves and cancels appointments, checks patients in,
and records the practitioners' working hours and time off. Each practitioner reads their own
agenda, and records what happened in each consultation in the patients' clinical records.

It was built in stages, adding nothing before the stage that used it. The functional scope is
in [`docs/context.md`](docs/context.md), every technical decision and its reasoning in
[`docs/decisions.md`](docs/decisions.md), and the progress in [`docs/build.md`](docs/build.md).
**Version 1 is complete. Version 2 is built in phases; five are closed — the clinical record, the
arrival queue, users and security, the patient's extra data, and reminders with the cancellation log —
the sixth, web booking for patients, is under way, and v2 is not finished.**

## Running it

- **JDK 21.** On this machine it is at `C:\Program Files\Java\jdk-21`, while the `java` on the
  `PATH` is 25, so point `JAVA_HOME` at 21 first:

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
  ```

- Maven does not need to be installed: the repository ships the wrapper.
- **Docker**, for the database.

```powershell
docker compose up -d        # PostgreSQL 18, with its data kept in a volume, and Mailpit for the
                            # reminders' email: it keeps them at http://localhost:8025 and sends none on
.\mvnw spring-boot:run      # the application, on http://localhost:8080
.\mvnw test                 # unit tests: nothing needed, no database
.\mvnw verify               # unit and integration tests: H2 in memory, no Docker needed;
                            # the PostgreSQL tests run too when Docker is up, and are skipped otherwise
```

Flyway creates the schema on the first start, and the data stays between restarts;
`docker compose down -v` wipes it. It starts with development data: two practitioners with their
reasons and weeks, three patients, and these users.

| User        | Password       | Role                                       |
|-------------|----------------|--------------------------------------------|
| `recepcion` | `recepcion123` | Reception: everything                      |
| `arias`     | `arias123`     | Practitioner: Dra. Arias's agenda and consultations |
| `benitez`   | `benitez123`   | Practitioner: Dr. Benítez's agenda and consultations |
| `admin`     | `admin123`     | Admin: the users, and nothing else         |
| `sala`      | `sala123`      | Waiting room: the screen on the wall, and nothing else |

They live in `src/main/resources/db/seed`, a Flyway location of its own that a real deployment
removes (D-34). The admin creates everyone else's users, one per person, at `/users` (D-68), with a temporary
password each one changes at `/password` on logging in (D-69). One of the patients, Laura Medina, has a web
account in use: she logs in at http://localhost:8080/portal/login as `DNI 27333444` / `paciente123` (D-80).

## Using it

**Screens** — open http://localhost:8080 and log in. Reception gets the agenda — a day or a
week, of one practitioner or everyone — with the day's operations as buttons (*Llegó*,
*Avisó demora*, *Mover*, *Cancelar*), a booking screen that suggests the free times and registers
and books a new patient at once, with their phone and email, the cancellations — who cancelled,
when, how and who asked —, what happened to each reminder, and whom to call, a patients
screen — where a patient who gives no name is registered under an alias, and each patient's
identity is corrected and verified, with its history, and their date of birth, phone and email
recorded, each saying whose it is, and their health insurance, and who agreed to be reminded on each, with a history of their own — each patient's appointments, cancelled ones included, a practitioners screen to set
up their reasons and week and record their absences, a queues screen to set up the arrival
queues and who serves each, the arrivals screen, where queues are opened and closed and patients
brought in, and the waiting-room screen, which names nobody. The queue screens show each change as
it happens. A practitioner gets their own agenda,
read-only, with today's patients linked to their clinical record and *Empezar consulta* once
they arrived: a form beside the record, sent once and never changed, and completed afterwards
with addenda. Under *Mi cola* they call the next patient of their queue and start their consultation. Under *Resultados pendientes* they find the checks waiting for a result, and ask
for access to a record to report one; under *Mis órdenes*, they follow up the orders they issued. The screens are in
Spanish, for the staff.

**The patients' screens** — at http://localhost:8080/portal/login, apart from the staff's. A patient whom
reception opened a web account for — from *Cuenta web* on the patient's screen, for a verified patient of
13 or older — gets a link by email to activate it and choose a password, logs in with their document and
reads their appointments: those ahead and those of the last year, never reception's note. Under *Sacar turno*
they book the reasons reception opened to the web — "Se reserva por la web" on the practitioners' screen —, and
under *Mis datos* they change their phone, email and health insurance, and whether they want reminders on each. The emails go to
Mailpit in development, at http://localhost:8025.

**API** — basic authentication on every request: the staff with their username, a patient with their
document as its type and number, such as `DNI 27333444`. Errors share one body:
`{timestamp, status, error, message, fields?}`.

| Method | Route | What it does |
|---|---|---|
| `POST` | `/api/patients` | register a patient, with an optional typed document; `documentSeen` verifies it at once; optionally `birthDate`, `phone` and `email`, each with its `owner`, and `insurance` |
| `POST` | `/api/patients/unidentified` | register a patient who gives no name, under an alias such as NN-4821 |
| `PUT` | `/api/patients/{id}` | correct a name, add or remove the document; `documentSeen` verifies the identity |
| `GET` | `/api/patients/{id}`, `/api/patients?query=` | find patients by name, document or alias |
| `GET` | `/api/patients/{id}/identity-changes` | the history of a patient's identity: what changed, when and by whom |
| `PUT` | `/api/patients/{id}/details` | set the date of birth, the phone and the email, each saying whose it is, and the health insurance; one left out is cleared |
| `PUT` | `/api/patients/{id}/contact-consent` | who agreed to reminders on each channel — `{phone, email}`, each `PATIENT`, `OWNER` or left out for nobody; a new number, address or owner drops it |
| `GET` | `/api/patients/{id}/details-changes` | the history of a patient's details: which one changed, from what to what, when and by whom |
| `POST` | `/api/practitioners` | register a practitioner |
| `PUT` | `/api/practitioners/{id}` | rename |
| `GET` | `/api/practitioners`, `/api/practitioners/{id}` | with reasons, week and time off |
| `POST` | `/api/practitioners/{id}/reasons` | add a reason with its duration |
| `PUT` | `/api/practitioners/{id}/reasons/{reasonId}` | rename it, change its duration, or whether patients book it on the web (`webBookable`) |
| `PUT` | `/api/practitioners/{id}/weekly-schedule` | replace the week; a break is a gap between stretches |
| `POST` | `/api/appointments` | book; the duration follows from the reason |
| `POST` | `/api/appointments/{id}/reschedule` | move it, keeping its duration |
| `POST` | `/api/appointments/{id}/cancel` | cancel it, keeping the record — `{requestedBy: PATIENT \| PRACTICE, note?}`; who and when come from the session and the clock |
| `GET` | `/api/cancellations?practitionerId=&from=&to=` | the cancellations by the day they were made, with who, how, who asked, the patient and the note |
| `GET` | `/api/cancellation-log?practitionerId=&from=&to=` | the same for the admins, without the patient and the note |
| `GET` | `/api/reminders?from=&to=` | what happened to each appointment's reminder, by the appointment's day: sent, failed, or not sent and why — never the address |
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
| `POST` | `/api/appointment-arrivals` | a patient with an appointment today arrives: into their practitioner's queue, kept for them; the appointment answers when, as `arrivedAt` |
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
| `POST` | `/api/patient-accounts` | reception opens a patient's web account — `{patientId, email}`, for a verified patient of 13 or older — and the activation goes to that email |
| `GET` | `/api/patient-accounts?patientId=`, `/api/patient-accounts/{id}` | a patient's web account and its state; never its password |
| `POST` | `/api/patient-accounts/{id}/activation-resend`, `/email-change`, `/deactivate`, `/reactivate` | send the activation again, change the account's email, switch it off or on |
| `GET` | `/api/patient-accounts/{id}/changes` | what was done to the account, when and by whom |
| `POST` | `/api/portal/activation` | the patient activates their account — `{token, password}`, the token from the email's link; no login |
| `GET` | `/api/portal/appointments` | the patient logged in reads their appointments: those ahead and those of the last 12 months, without the note |
| `GET` | `/api/portal/profile` | the patient's own data: name, document and date of birth, their phone and email with whose each is and its consent, and their health insurance |
| `PUT` | `/api/portal/profile/details` | the patient sets their phone, email and health insurance, with reception's rules; one left out is cleared |
| `GET` | `/api/portal/booking/options`, `/api/portal/booking/times?practitionerId=&reasonId=&date=` | what the patient may book on the web: the reasons reception opened to it, the days from tomorrow to 30 days on, and the times offered |
| `POST` | `/api/portal/appointments` | the patient books for themselves, three ahead at most; a confirmation goes to the account's email |
| `PUT` | `/api/portal/profile/contact-consent` | `{phone, email}`: true is the patient's own consent, false withdraws any, left out stays |
| `GET` | `/api/clinical-records/{patientId}` | the patient's date of birth and health insurance, their consultations with their checks, and the checks done without one — for a practitioner who saw them within the access period or is seeing them today |

A booking whose patient did not arrive — into their practitioner's queue — is marked absent 30 minutes after its start, or at the end of its
day when the patient warned they would be late; an absent appointment is kept, shown on the agenda
and changed no more. The consultation routes are the
practitioners' only; anything a user may not see answers 403. The access period to a clinical
record is `app.clinical-record.access-period`, a year by default. Reminders go out by email once a day
(`app.reminders.cron`, 10:00) for the appointments of the next `app.reminders.days-ahead` days (3), to the
patients whose email has their consent — a responsible person's only for a patient under 13; the
practice's name, address and phone are `app.practice.*`, its sender `app.mail.from`, and the SMTP server
`spring.mail.*`. The links in the patients' emails point at `app.portal.base-url`, and an activation lasts
`app.portal.activation-valid-for`, 72 hours by default.

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
├── portal/                   hexagonal: the patients' side — their web accounts and what they read with them
├── practitioner/             layered alike, with the week's rules in plain classes
├── reminder/                 hexagonal: who is reminded of their appointment, sent by email once a day
├── security/                 the access rules: login, roles per route, who is asking
├── user/                     layered alike: the users, and the role each one has
└── shared/                   the clock; http/ holds the API's error body
```

A module reaches another only through its `api/` package, and in the layered modules each layer
is reached only by the one above it; `ArchitectureTest` checks both on every build (D-05, D-42).
The domains of `appointment`, `clinicalrecord`, `portal` and `reminder` have no framework annotations and are tested
without Spring. The whole repository is in English; on-screen text is in Spanish (D-13).

## Deliberately postponed

From the clinical record, marking results as reviewed, orders and files coming from elsewhere, and moving
the files to an object store. The next phases — the arrival queue, web booking and the rest — are
in the context's planned evolution.
