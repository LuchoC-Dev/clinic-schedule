# Clinic appointment schedule

An internal appointment schedule for a small medical practice (three or four
practitioners). Reception manages appointments, working hours and time off; each
practitioner reads only their own appointments.

The application is built in stages, adding nothing before the stage that uses it. The
functional scope lives in [`docs/context.md`](docs/context.md), the technical decisions in
[`docs/decisions.md`](docs/decisions.md) and the progress in
[`docs/build.md`](docs/build.md).

## Requirements

- **JDK 21.** On this machine it is at `C:\Program Files\Java\jdk-21`. The `java` on the
  `PATH` is 25, so `JAVA_HOME` has to point at 21 before building:

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
  ```

- Maven does not need to be installed: the repository ships the wrapper (`mvnw`).

## Commands

```powershell
.\mvnw test                # full test suite
.\mvnw spring-boot:run     # runs the app on http://localhost:8080
```

The database is in-memory H2, owned by Flyway and recreated on every start.

## Current state

**Stage 1 of 8 is done: the application starts.** That is all it does — there are no
endpoints yet, so every URL answers 404, and Flyway logs `No migrations found` because the
first migration arrives with the patient table in stage 2.

```
src/main/java/com/clinic/schedule/ScheduleApplication.java
src/main/resources/application.yml
src/test/java/com/clinic/schedule/ScheduleApplicationTests.java
```

The plan from here: patients (2), practitioners with their reasons and working hours (3),
appointments with the booking rules (4), check-in and automatic cancellation (5), time off
(6), the agenda screens (7) and security with login and roles (8).

Each stage brings its own dependencies with it. Security, views, validation and the
scheduler are deliberately absent until then, so the repository shows every piece arriving
with the reason that justifies it (D-14).

## Conventions

The whole repository is written in English — code, SQL, comments, documents and commit
messages. On-screen text for the staff of the practice stays in Spanish (D-13).
