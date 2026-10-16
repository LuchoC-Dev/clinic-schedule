# Clinic appointment schedule

An internal appointment schedule for a small medical practice — three or four practitioners and one reception desk —
with a web portal for its patients. It was built in stages, adding nothing before the stage that used it. **Version 1
and version 2 are complete**; what comes next is added to v2 as features of their own.

## What it does

- **Reception** books, moves and cancels appointments, registers patients and their details, checks them into the
  practitioners' queues, charges each visit at the cash desk, sets up the practitioners' weeks and time off, and
  opens the patients' web accounts.
- **Practitioners** call the next patient from their queue, write the consultation in the clinical record — with
  prescriptions, checks, orders, addenda and files — and authenticate the copies of the record patients ask for.
- **Admins** manage the users and read the logs: who read which record, the cancellations and the cash desk.
- **The waiting room's screen** shows whom each practitioner called and who waits next.
- **Patients**, on their phone, register or activate their account, book, move and cancel their appointments and
  their family's, keep their details up to date, and ask for a copy of their clinical record.

Reminders go out by email the days before each appointment. Every screen is in Spanish; the repository is in
English (D-13).

## Stack

Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA · Flyway · PostgreSQL 18 — and two single-page
applications in React with TypeScript, Vite, Mantine, React Router and TanStack Query (D-97). Tested with JUnit on H2
and PostgreSQL, Vitest, and Playwright end to end.

## Running it

You need **JDK 21** with `JAVA_HOME` pointing at it — the wrapper uses `JAVA_HOME`, not the `java` on the `PATH` — and
**Docker**. Maven and Node.js need not be installed: the repository ships the wrapper, and the build installs its own
Node.js into `frontend/node`.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"   # if it is not set already
docker compose up -d        # PostgreSQL on port 5433, and Mailpit for the emails at http://localhost:8025
.\mvnw spring-boot:run      # the application, on http://localhost:8080
```

The staff's application is at http://localhost:8080 and the patients' at http://localhost:8080/portal/. The data stays
between restarts; `docker compose down -v` wipes it.

### Development users

| User        | Password       | Role |
|-------------|----------------|------|
| `recepcion` | `recepcion123` | Reception |
| `arias`     | `arias123`     | Practitioner (Dra. Arias) |
| `benitez`   | `benitez123`   | Practitioner (Dr. Benítez) |
| `admin`     | `admin123`     | Admin |
| `sala`      | `sala123`      | The waiting room's screen |

A patient, Laura Medina, logs in to the portal with `DNI 27333444` / `paciente123`. The development data lives in
`src/main/resources/db/seed`, which a real deployment leaves out (D-34).

### Tests and the frontend

```powershell
.\mvnw test                 # unit tests and the applications' Vitest tests: no database needed
.\mvnw verify               # integration tests too, on H2; the PostgreSQL ones run when Docker is up,
                            # and the end-to-end ones download Chromium the first time
```

For a change in Java alone, `-Dskip.npm -Dskip.installnodenpm` skips building the applications. To work on one of
them with Vite, while the application runs on port 8080:

```powershell
cd frontend
npm run dev:staff           # http://localhost:5173/
npm run dev:patients        # http://localhost:5174/portal/
npm test
```

## How it is built

```
frontend/src/
├── staff/          the staff's application, for a desktop, at /
├── patients/       the patients', phone first, at /portal/
└── shared/         the theme, the API client, the words for each refusal

src/main/java/com/clinic/schedule/
├── appointment/    the bookings                         hexagonal
├── arrival/        the queues                           event sourcing and CQRS
├── cashdesk/       the visits' charges and refunds      hexagonal
├── clinicalrecord/ consultations, checks, orders, files hexagonal
├── patient/        the patients and their details       layered
├── portal/         the patients' web accounts           hexagonal
├── practitioner/   reasons, weeks and time off          layered
├── reminder/       the reminders by email               hexagonal
├── security/       login, roles per route, who is asking
├── user/           the staff's users                    layered
└── shared/         the clock, the API's error body, serving the applications
```

A module reaches another only through its `api/` package, which `ArchitectureTest` checks on every build (D-05, D-42).

## Documents

- [`docs/context.md`](docs/context.md) — what the application does, for whom, and its business rules.
- [`docs/decisions.md`](docs/decisions.md) — every technical decision and its reasoning.
- [`docs/build.md`](docs/build.md) — the stages and phases as they were built, and what was left for later.
- [`docs/api.md`](docs/api.md) — the REST API: logging in, every route, and the settings.
