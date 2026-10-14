# Clinic appointment schedule

An internal appointment schedule for a small medical practice — three or four practitioners
and one reception desk. Reception books, moves and cancels appointments, checks patients in,
and records the practitioners' working hours and time off. Each practitioner reads their own
agenda, and records what happened in each consultation in the patients' clinical records.

It was built in stages, adding nothing before the stage that used it. The functional scope is
in [`docs/context.md`](docs/context.md), every technical decision and its reasoning in
[`docs/decisions.md`](docs/decisions.md), and the progress in [`docs/build.md`](docs/build.md).
**Version 1 is complete. Version 2 is built in phases; nine are closed — the clinical record, the
arrival queue, users and security, the patient's extra data, reminders with the cancellation log, web
booking for patients, the cash desk, registering from the web, and moving an appointment with a copy of
the clinical record — and v2 is not finished.**

## Running it

- **JDK 21**, with `JAVA_HOME` pointing at it: the wrapper uses `JAVA_HOME`, not the `java` on
  the `PATH`, which may be a newer JDK. The Temurin installer sets `JAVA_HOME` for the whole
  machine; if it is not set, or points at another JDK, set it for the session:

  ```powershell
  winget install --id EclipseAdoptium.Temurin.21.JDK -e   # if there is no JDK 21 yet
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
  ```

- Maven does not need to be installed: the repository ships the wrapper.
- Nor does Node.js: the build installs its own into `frontend/node` the first time, for the web applications.
- **Docker**, for the database.

```powershell
docker compose up -d        # PostgreSQL 18, with its data kept in a volume, and Mailpit for the
                            # reminders' email: it keeps them at http://localhost:8025 and sends none on
.\mvnw spring-boot:run      # the application, on http://localhost:8080
.\mvnw test                 # unit tests: nothing needed, no database
.\mvnw verify               # unit and integration tests: H2 in memory, no Docker needed;
                            # the PostgreSQL tests run too when Docker is up, and are skipped otherwise;
                            # the end-to-end tests download Chromium the first time
```

Every build also builds the two web applications under `frontend/` — type-checked, linted and bundled into the
jar — and `test` runs their tests with Vitest. For a change in Java alone, `-Dskip.npm -Dskip.installnodenpm`
leaves them as they were built last. To work on one of them, with the application running on port 8080:

```powershell
cd frontend
npm run dev:staff           # the staff's application, at http://localhost:5173/app/
npm run dev:patients        # the patients', at http://localhost:5174/portal/app/
npm test                    # their tests
```

Vite serves the application and sends everything else — the API, the Thymeleaf screens — to port 8080, so the
session is one.

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

**The web applications** — the screens are moving to two single-page applications (D-97): the staff's at
http://localhost:8080/app/, the patients' at http://localhost:8080/portal/app/. For now, the staff's has reception's
agenda — a day or its week, of everyone or one practitioner, with the arrival charged as it comes in, a warning of
lateness, moving and cancelling —, booking at the free times with a patient found or a new one, and the patients, with
their details, consent, histories and appointments; a very large amount is confirmed a second time. Every other entry
of each role's menu opens its Thymeleaf screen, in the same session; the patients' has its login, with the document, and its sections lead to their screens. Both keep nothing in
the browser, and say every refusal in Spanish.

**Screens** — open http://localhost:8080 and log in. Reception gets the agenda — a day or a
week, of one practitioner or everyone — with the day's operations as buttons (*Llegó*,
*Avisó demora*, *Mover*, *Cancelar*), a booking screen that suggests the free times and registers
and books a new patient at once, with their phone and email, the cancellations — who cancelled,
when, how and who asked —, what happened to each reminder, and whom to call, a patients
screen — where a patient who gives no name is registered under an alias, and each patient's
identity is corrected and verified, with its history, and their date of birth, phone and email
recorded, each saying whose it is, and their health insurance, and who agreed to be reminded on each, with a history of their own — each patient's appointments, their family groups — formed at the desk, a child at a parent's request, with who shares their appointments and a history —, cancelled ones included, a practitioners screen to set
up their reasons and week and record their absences, a queues screen to set up the arrival
queues and who serves each, the arrivals screen, where queues are opened and closed and patients
brought in — each visit charged as the patient comes in, in cash, by card, transfer or QR, or covered by
their health insurance, with its token and copay, an emergency's left pending —, the day's cash desk, with
its charges and refunds — some or all of a charge given back, by its means, saying why, and a visit given
back whole charged again —, their totals by means and by who recorded them, with what should be in the
drawer, the emergencies still to charge or cover, and the visits covered that day, where a coverage marked
by mistake is withdrawn, and the
waiting-room screen, which names nobody. The queue screens show each change as
it happens. A practitioner gets their own agenda,
read-only, with today's patients linked to their clinical record and *Empezar consulta* once
they arrived: a form beside the record, sent once and never changed, and completed afterwards
with addenda. Under *Mi cola* they call the next patient of their queue and start their consultation. Under *Resultados pendientes* they find the checks waiting for a result, and ask
for access to a record to report one; under *Mis órdenes*, they follow up the orders they issued. On the record, under
*Archivos de la historia*, they upload what is not in the application — a record kept on paper, a report never
uploaded. Under *Copias pedidas* they find the copies of records the patients asked for, with the time left of the 48
hours: they review one, upload what was missing and put it together again, and authenticate it — or ask for access to
the record first —, and check a copy against the application by its code. Reception hands the copies on paper over,
sealed, under *Copias para retirar*. The screens are in Spanish, for the staff.

**The patients' screens** — at http://localhost:8080/portal/login, apart from the staff's. A patient whom
reception opened a web account for — from *Cuenta web* on the patient's screen, for a verified patient of
13 or older — gets a link by email to activate it and choose a password; an adult of 18 or older registers
themselves from "Registrate" on the login, with their children under 13 if they want, and confirms it from a link to
their email, unverified and with one appointment ahead each until reception sees their documents; an adult also
registers a child later, from *Mi familia*. The children go into a family group with the relation not proved, which
reception proves on the group's screen. A patient logs in with their document and
reads their appointments: those ahead and those of the last year, never reception's note. Five failed logins in a
row lock the account for 15 minutes; "¿Olvidaste tu contraseña?" sends a link to the account's email to choose
a new one, and *Contraseña* changes it. Under *Mis turnos* they move an appointment to another time the agenda
offers, with the same practitioner and reason — "· Movido por la web" on reception's agenda —, or cancel it, up to a
day before it, as anyone cancels from the link in its reminder or in a booking's confirmation; under *Sacar turno*
they book the reasons reception opened to the web — "Se reserva por la web" on the practitioners' screen —, under
*Mi familia* they see their family groups and choose whether each sees their appointments, and from 18 they see,
book, move and cancel those of the members who share theirs as their own, and
under *Mis datos* they change their phone, email and health insurance, and whether they want reminders on each. Under *Mi
historia clínica* they ask for a copy of their whole clinical record — from 13, their own; a parent, that of their
child under 13 once reception proved the relation —, as a PDF to download once a practitioner authenticates it, or on
paper to collect at reception. The emails go to Mailpit in development, at http://localhost:8025.

**API** — the web applications log in with a session, as the screens do, and send its CSRF token as `X-CSRF-TOKEN`
with every write; any other client uses basic authentication on every request: the staff with their username, a
patient with their document as its type and number, such as `DNI 27333444`. Errors share one body:
`{timestamp, status, error, message, code?, fields?}`, `code` naming a refusal for a screen to say it in its words,
such as `PRACTITIONER_ALREADY_BOOKED` or `ALREADY_CHARGED`.

| Method | Route | What it does |
|---|---|---|
| `GET` | `/api/session`, `/api/portal/session` | who of the staff is logged in, or whether a patient is, and the CSRF token, before logging in too; the patients', the types of document too |
| `POST` | `/api/session`, `/api/portal/session` | log in, as a form — `username` and `password`; `documentType`, `documentNumber` and `password` —, with the token: the same as above, with the new token, or a 401; a patient's failures count towards the lock |
| `POST` | `/api/session/logout`, `/api/portal/session/logout` | log out, ending the whole session, the screens' too |
| `GET` | `/api/agenda?date=&practitionerId=&week=` | reception's agenda of a day, or with `week=true` of its week from Monday to Sunday — today without one, everyone's without a practitioner —: the practitioners to choose from, and each appointment not cancelled with its patient's and practitioner's names |
| `GET` | `/api/agenda/free-times?practitionerId=&reasonId=&date=`, `?appointmentId=&date=` | the times free that day for a reason of the practitioner, or to move an appointment to, and whether the practitioner works that day at all |
| `POST` | `/api/appointments/with-new-patient` | register a patient and book them at once — the booking with `patient: {fullName, documentType?, documentNumber?, phone?, email?}` in place of `patientId`, the phone and the email each `{value, owner?: {name, relation}}` —; nothing of either when one is refused |
| `GET` | `/api/patients/{id}/appointments` | a patient's appointments, cancelled ones included, each with who cancelled it, when, how and who asked |
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
| `POST` | `/api/queues/{id}/session/patients`, `/session/unidentified` | bring a patient in, or one who gives no name, as an emergency or not, with the `amount` charged — which only an emergency may leave out, pending — and its `means`: `CASH`, the default, `DEBIT`, `CREDIT`, `TRANSFER` or `QR`; a known patient with an insurance and a verified identity may come in with `coverage: {token?}`, the `amount` then being the copay, if any |
| `POST` | `/api/queue-entries/{id}/emergency`, `/left` | make a waiting patient an emergency, or say they left |
| `POST` | `/api/appointment-arrivals` | a patient with an appointment today arrives, paying the `amount` by its `means`, or with `coverage: {token?}` and its copay: into their practitioner's queue, kept for them; the appointment answers when, as `arrivedAt` |
| `GET` | `/api/cash-desk?date=` | a day's cash desk, today by default: each charge and refund with its patient — a refund with why, a charge with what is left of it —, what was charged, given back and is left, overall and by who recorded them, each by means, with the `drawer` — what is left in cash —, each movement saying whether it was a `copay`, the emergencies of that day never charged nor covered, and the visits `covered` that day, with the insurance as it was then, the token and any withdrawal |
| `POST` | `/api/visits/{id}/charge` | `{amount, means}`: charge an emergency's pending visit, or one given back whole again; cash unless `means` says otherwise |
| `POST` | `/api/visits/{id}/refund` | `{amount, reason}`: give back some or all of what is left of the visit's charge, by its means, saying why |
| `POST` | `/api/visits/{id}/coverage` | `{token?, copay?, means?}`: record an emergency's pending visit as covered by the patient's insurance |
| `POST` | `/api/visits/{id}/coverage/withdrawal` | `{reason}`: withdraw a coverage marked by mistake, once its copay is given back whole; the visit is then charged privately |
| `GET` | `/api/cash-desk-log?date=` | the same for the admins, without the patients, the refunds' reasons, the pending visits and the covered ones |
| `POST` | `/api/queue-entries/{id}/reassign`, `/lift-reservation` | give a waiting patient to another practitioner of the queue, or to anyone |
| `GET` | `/api/my-queue` | a practitioner's own queue |
| `POST` | `/api/my-queue/call-next`, `/finish`, `/did-not-come-in` | call the next patient, finish with them, or say they did not come in |
| `POST` | `/api/consultations` | a practitioner records the consultation of a patient who arrived today — notes, prescriptions, the checks done and the orders issued |
| `POST` | `/api/consultations/{id}/addenda` | an addendum to a consultation already sent, by any practitioner with access to the record |
| `POST` | `/api/checks` | a check without a consultation — the emergency precaution — with its result or pending; it may fulfil an open order |
| `POST` | `/api/checks/{id}/addenda` | an addendum to a check — how a wrong result is corrected — by any practitioner with access to the record |
| `POST` | `/api/orders/{id}/cancel` | the author cancels an open order, with a reason |
| `POST` | `/api/consultations/{id}/attachments`, `/api/checks/{id}/attachments`, `/api/clinical-records/{patientId}/attachments` | attach a PDF, PNG or JPEG of up to 10 MB to a consultation, a check or the record as a whole, as `multipart/form-data` with a `file` part |
| `GET` | `/api/attachments/{id}` | download an attached file — only for whoever may read the record |
| `GET` | `/api/orders/mine` | the user's own orders not cancelled, and where each stands — no result texts |
| `POST` | `/api/checks/{id}/result` | the result of a pending check, by any practitioner with access to the record |
| `GET` | `/api/checks/pending` | the checks of the practice waiting for a result: only the patient and the check |
| `POST` | `/api/checks/{id}/access-grants` | access to the record to report that pending check — granted at once, for now, and recorded |
| `GET` | `/api/record-copies`, `?code=`, `/api/record-copies/{id}` | the copies of records waiting for a practitioner, the soonest due first, with the minutes left of the 48 hours and whether the user may read the record; one by the code printed on it, or by id |
| `GET` | `/api/record-copies/{id}/pdf` | the copy as a PDF, as the record stood when it was put together — a reading of the record |
| `POST` | `/api/record-copies/{id}/reassembly`, `/authentication`, `/access-grants` | put it together again as the record stands now; authenticate it — `{assembly, reviewed}`, the assembly reviewed —; or ask for access to its record to do so — `{reason}` |
| `GET` | `/api/record-copy-envelopes` | reception's copies on paper waiting to be collected: whose, who asked, since when |
| `POST` | `/api/record-copy-envelopes/{id}/handover` | `{documentSeen}`: reception hands one over to whoever shows their document |
| `POST` | `/api/patient-accounts` | reception opens a patient's web account — `{patientId, email}`, for a verified patient of 13 or older — and the activation goes to that email |
| `GET` | `/api/patient-accounts?patientId=`, `/api/patient-accounts/{id}` | a patient's web account and its state; never its password |
| `POST` | `/api/patient-accounts/{id}/activation-resend`, `/email-change`, `/deactivate`, `/reactivate` | send the activation again, change the account's email, switch it off or on |
| `GET` | `/api/patient-accounts/{id}/changes` | what was done to the account, when and by whom |
| `POST` | `/api/family-groups` | reception forms a family group — `{name}` |
| `GET` | `/api/family-groups`, `?patientId=`, `/api/family-groups/{id}` | the family groups by name, or a patient's, with each member and whether the group sees their appointments |
| `POST` | `/api/family-groups/{id}/members` | someone of 13 or older joins, present and agreeing — `{patientId, shares}`, `shares` an adolescent's choice; an adult shares |
| `POST` | `/api/family-groups/{id}/children` | a child under 13, at the request of an adult of the group — `{patientId, requestedBy, relation, proofSeen}`, `relation` `MOTHER`, `FATHER` or `GUARDIAN` |
| `POST` | `/api/family-groups/{id}/members/{patientId}/relation-proof` | `{proofSeen}`: reception saw what proves the relation of a child an adult registered on the web |
| `POST` | `/api/family-groups/{id}/members/{patientId}/remove`, `/sharing` | take a member out; a member of 13 or older starts or stops sharing — `{shares}` |
| `GET` | `/api/family-groups/{id}/changes` | what happened to the group, the most recent first, and by whom |
| `POST` | `/api/portal/registrations` | an adult of 18 or older registers themselves — `{fullName, documentType, documentNumber, birthDate, phone, email, password, children}`, up to five children under 13 each `{fullName, documentType, documentNumber, birthDate, relation}` —; 202 once the email went out, the same whether the practice knows a document or not; no login |
| `POST` | `/api/portal/registrations/confirmation` | `{token}`, from that email's link: the patient is registered, unverified, with their account in use, and their children in a family group; 200 with `notRegistered`, the children it could not register; 409 when the adult's document was someone's by then; no login |
| `POST` | `/api/portal/family/children` | the adult logged in registers a child under 13 — `{fullName, documentType, documentNumber, birthDate, relation, groupId}`, a new group without `groupId` —; 202 whether the practice knows the document or not; 429 past five a day |
| `POST` | `/api/portal/activation` | the patient activates their account — `{token, password}`, the token from the email's link; no login |
| `POST` | `/api/portal/password-recovery` | `{documentType, documentNumber}`: sends a link to choose a new password to the account's email, valid for an hour; always 204, account or not; no login |
| `POST` | `/api/portal/password-reset` | `{token, password}`: the new password, with the token from that link; it lifts a lock and ends the account's open sessions; no login |
| `POST` | `/api/portal/password` | `{currentPassword, newPassword}`: the patient logged in changes their password |
| `GET` | `/api/portal/appointments` | the patient logged in reads their appointments: those ahead and those of the last 12 months, without the note; from 18, also those of each family member who shares theirs, under `family` |
| `GET` | `/api/portal/profile` | the patient's own data: name, document and date of birth, their phone and email with whose each is and its consent, and their health insurance |
| `PUT` | `/api/portal/profile/details` | the patient sets their phone, email and health insurance, with reception's rules; one left out is cleared |
| `GET` | `/api/portal/booking/options`, `/api/portal/booking/times?practitionerId=&reasonId=&date=` | what the patient may book on the web: the reasons reception opened to it, the days from tomorrow to 30 days on, for whom — themselves first — and the times offered |
| `POST` | `/api/portal/appointments` | the patient books for themselves, or with `patientId` for a family member they act for, three ahead at most for whom it is; a confirmation goes to the account's email |
| `GET` | `/api/portal/appointments/{id}/move-times?date=` | the times one of their appointments, or a family member's they act for, may be moved to that day |
| `POST` | `/api/portal/appointments/{id}/move` | `{startsAt}`: the patient moves it to one of those times, from tomorrow to 30 days on, up to a day before it; the same appointment, marked `movedOnWeb`, and a confirmation goes to the account's email |
| `POST` | `/api/portal/appointments/{id}/cancel` | the patient cancels one of their appointments, or of a family member they act for, up to a day before it |
| `GET` | `/api/portal/family` | the patient's family groups: each member's name, age band and whether they share, and which is the patient |
| `GET` | `/api/portal/record-copies` | whose record the patient may ask a copy of — `askableFor` —, and the copies asked for, with where each stands |
| `POST` | `/api/portal/record-copies` | `{patientId?, delivery}`: ask for a copy of the patient's own record, or of a child's under 13 with the relation proved, `PDF` or `PAPER`; one waits at a time |
| `GET` | `/api/portal/record-copies/{id}/pdf` | download an authenticated PDF copy — a reading of the record in the account's name |
| `POST` | `/api/portal/family/{groupId}/sharing`, `/api/portal/family/{groupId}/leave` | the patient shares their appointments with the group or stops — `{shares}` —, or leaves it |
| `GET`, `POST` | `/api/portal/cancel-links/{token}`, `/api/portal/cancel-links/{token}/cancel` | what an email's link would cancel — when and with whom — and cancelling it, with nobody logged in |
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
`app.portal.activation-valid-for`, 72 hours by default, as a registration made on the web waits for its
confirmation, `app.portal.registration-valid-for`, before it is forgotten; an account asks to register up to
`app.portal.children-per-day` children a day, five by default. A charge is pesos above zero with two decimals at
most, up to `app.cash-desk.max-amount`, $ 99.999.999,99 by default; it is paid in cash, by debit or credit card,
by transfer or by QR, is never changed nor deleted, and what went wrong is given back as a refund of its own,
by the charge's means. Only cash counts for what should be in the drawer. A visit goes in covered only for a
patient with an insurance recorded and a verified identity; the insurer is asked outside the application, and
its copay, if any, is a charge as any other.

## How it is built

```
frontend/                     the web applications, React with TypeScript, built by Vite (D-97)
├── src/staff/                the staff's, for a desktop, at /app/
├── src/patients/             the patients', phone first, at /portal/app/
└── src/shared/               the theme, the API client, the words for a refusal, the practice's dates

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
├── cashdesk/                 hexagonal: each visit's charges and refunds, and the day's cash desk
├── clinicalrecord/           hexagonal too: consultations, checks, orders, attachments, and who may read a record
├── patient/                  layered: presentation/ (http, screen), service/, entity/,
│                             persistence/ — and api/, its only door for other modules
├── portal/                   hexagonal: the patients' side — their web accounts and what they read with them
├── practitioner/             layered alike, with the week's rules in plain classes
├── reminder/                 hexagonal: who is reminded of their appointment, sent by email once a day
├── security/                 the access rules: login, roles per route, who is asking
├── user/                     layered alike: the users, and the role each one has
└── shared/                   the clock; http/ holds the API's error body, webapp/ serves the web applications
```

A module reaches another only through its `api/` package, and in the layered modules each layer
is reached only by the one above it; `ArchitectureTest` checks both on every build (D-05, D-42).
The domains of `appointment`, `cashdesk`, `clinicalrecord`, `portal` and `reminder` have no framework annotations and are tested
without Spring. The whole repository is in English; on-screen text is in Spanish (D-13).

## Deliberately postponed

From the clinical record, marking results as reviewed, orders and files coming from elsewhere, and moving
the files to an object store. The next phases — the arrival queue, web booking and the rest — are
in the context's planned evolution.
