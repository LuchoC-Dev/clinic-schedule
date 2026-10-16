# The API

The REST API is the contract both web applications use (D-97). This page lists its routes; why each works as it does is
in [`decisions.md`](decisions.md), and the business rules behind them in [`context.md`](context.md).

## Logging in

It is logged in to with a session and nothing else (D-115): the staff at `POST /api/session` with their
username and password, a patient at `POST /api/portal/session` with their document's type and number and their
password, each after asking `GET /api/session` or `GET /api/portal/session` for the CSRF token, sent as
`X-CSRF-TOKEN` with every write and with the login. Errors share one body:
`{timestamp, status, error, message, code?, fields?}`, `code` naming a refusal for a screen to say it in its words,
such as `PRACTITIONER_ALREADY_BOOKED` or `ALREADY_CHARGED`.

What a role may reach is decided by route, and whose data a user may see by the use case: anything a user may not see
answers 403 (D-34, D-50). The clinical routes are the practitioners' only (D-51).

## Routes

| Method | Route | What it does |
|---|---|---|
| `GET` | `/api/session`, `/api/portal/session` | who of the staff is logged in, or whether a patient is, and the CSRF token, before logging in too; the patients', the types of document too |
| `POST` | `/api/session`, `/api/portal/session` | log in, as a form — `username` and `password`; `documentType`, `documentNumber` and `password` —, with the token: the same as above, with the new token, or a 401; a patient's failures count towards the lock |
| `POST` | `/api/session/logout`, `/api/portal/session/logout` | log out, ending the whole session |
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
| `GET` | `/api/practitioners`, `/api/practitioners/{id}` | with reasons, week and time off; the list, the admins too |
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
| `POST` | `/api/time-off` | a practitioner's absence — whole days up to `untilDate`, or part of one — cancelling the appointments it hits, with their patients' names |
| `POST`, `GET` | `/api/queues` | set up a queue — a name nobody else has, an optional room — and list them with who serves each |
| `GET`, `PUT` | `/api/queues/{id}` | read a queue, rename it or change its room |
| `POST` | `/api/queues/{id}/practitioners` | a practitioner starts serving the queue; one queue at most each |
| `DELETE` | `/api/queues/{id}/practitioners/{practitionerId}` | a practitioner stops serving it; the queue stays |
| `POST` | `/api/queues/{id}/open`, `/close`, `/auto-close-off` | open a queue — a new session —, close it, keep it open past its day |
| `GET` | `/api/queues/{id}/session` | the open session: who waits, in the order they will be called, who was called, who is done — each with their name and document and the names of whom they wait for and who called them |
| `POST` | `/api/queues/{id}/session/patients`, `/session/unidentified` | bring a patient in, or one who gives no name, as an emergency or not, with the `amount` charged — which only an emergency may leave out, pending — and its `means`: `CASH`, the default, `DEBIT`, `CREDIT`, `TRANSFER` or `QR`; a known patient with an insurance and a verified identity may come in with `coverage: {token?}`, the `amount` then being the copay, if any |
| `POST` | `/api/queue-entries/{id}/emergency`, `/left` | make a waiting patient an emergency, or say they left |
| `POST` | `/api/appointment-arrivals` | a patient with an appointment today arrives, paying the `amount` by its `means`, or with `coverage: {token?}` and its copay: into their practitioner's queue, kept for them; the appointment answers when, as `arrivedAt` |
| `GET` | `/api/cash-desk?date=` | a day's cash desk, today by default: each charge and refund with its patient — a refund with why, a charge with what is left of it —, what was charged, given back and is left, overall and by who recorded them, each by means, with the `drawer` — what is left in cash —, each movement saying whether it was a `copay`, the emergencies of that day never charged nor covered, and the visits `covered` that day, with the insurance as it was then, the token and any withdrawal; each movement says whether it is `refundable` and `chargeableAgain`, each covered visit whether it is `chargeable` |
| `POST` | `/api/visits/{id}/charge` | `{amount, means}`: charge an emergency's pending visit, or one given back whole again; cash unless `means` says otherwise |
| `POST` | `/api/visits/{id}/refund` | `{amount, reason}`: give back some or all of what is left of the visit's charge, by its means, saying why |
| `POST` | `/api/visits/{id}/coverage` | `{token?, copay?, means?}`: record an emergency's pending visit as covered by the patient's insurance |
| `POST` | `/api/visits/{id}/coverage/withdrawal` | `{reason}`: withdraw a coverage marked by mistake, once its copay is given back whole; the visit is then charged privately |
| `GET` | `/api/cash-desk-log?date=` | the same for the admins, without the patients, the refunds' reasons, the pending visits and the covered ones |
| `POST` | `/api/queue-entries/{id}/reassign`, `/lift-reservation` | give a waiting patient to another practitioner of the queue, or to anyone |
| `GET` | `/api/waiting-room` | every queue as the waiting room shows it: whom it called and who waits, by name alone |
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
| `GET` | `/api/family-groups`, `?patientId=`, `/api/family-groups/{id}` | the family groups by name, or a patient's, with each member, their `ageBand` — `CHILD`, `ADOLESCENT` or `ADULT` — and whether the group sees their appointments |
| `POST` | `/api/family-groups/{id}/members` | someone of 13 or older joins, present and agreeing — `{patientId, shares}`, `shares` an adolescent's choice; an adult shares |
| `POST` | `/api/family-groups/{id}/children` | a child under 13, at the request of an adult of the group — `{patientId, requestedBy, relation, proofSeen}`, `relation` `MOTHER`, `FATHER` or `GUARDIAN` |
| `POST` | `/api/family-groups/{id}/members/{patientId}/relation-proof` | `{proofSeen}`: reception saw what proves the relation of a child an adult registered on the web |
| `POST` | `/api/family-groups/{id}/members/{patientId}/remove`, `/sharing` | take a member out; a member of 13 or older starts or stops sharing — `{shares}` |
| `GET` | `/api/family-groups/{id}/changes` | what happened to the group, the most recent first, and by whom, naming whom it was about |
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

## What the routes do on their own, and their settings

A booking whose patient did not arrive — into their practitioner's queue — is marked absent 30 minutes after its start, or at the end of its
day when the patient warned they would be late; an absent appointment is kept, shown on the agenda
and changed no more. The access period to a clinical
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
