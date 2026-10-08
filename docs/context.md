# Project context

## What this is

An internal appointment schedule for a small medical practice (three or four
practitioners). A Spring Boot application built in stages: every version delivers
something useful and never anticipates future features.

The repository has two goals: solve the real scheduling problem, and show how an
application grows in an orderly way.

## Who uses it (v1)

- **Reception**: manages appointments, working hours and time off for every practitioner.
  Can read anyone's agenda, including to show it to the practitioner.
- **Practitioner**: reads only their own appointments. To change working hours or record
  time off, they ask reception.

Patients have **no** access at this stage. They do not book appointments and have no account.

## Scope of v1

### Patients

The minimum record needed to book an appointment:

- Internal identifier, full name, an optional identity document: a type (DNI, LE, LC, CI,
  passport) and a number.
- The document is **not** the internal identifier. When given, it is unique.
- Patients can be registered without a document.
- Several patients may share the same name.
- There is no patient file and no login account.

### Practitioners and availability

- A usual weekly schedule, breaks and time off. Reception configures all of it.
- One practitioner per consulting room: no shared resources to manage.
- Reception records a practitioner's time off for a whole day, for a time range, or for
  several whole days. The
  system automatically cancels the affected appointments that have **no** confirmed
  patient check-in. Appointments with a confirmed check-in need manual handling.

### Reasons and duration

- Every practitioner has their own short list of reasons: "consultation" plus two or three
  more, depending on the practitioner.
- Every reason has a fixed duration **for that practitioner**.
- When booking, the application layer looks up that duration and hands it to the booking
  domain. The domain works with a start time and a duration, and knows nothing about what
  the reason means.
- The duration is the time reserved in the agenda. The actual visit may run longer; v1
  does not try to absorb those delays.

### Appointments

- Reception creates appointments (same-day included), reschedules them and cancels them.
- An appointment has a patient, a practitioner, a start date and time, a duration, a reason
  and an optional note.
- Initial states: `BOOKED` and `CANCELLED`. Cancelled appointments are kept as records.
- Rescheduling updates the appointment. No history of previous dates is kept.
- No overlapping appointments for the same practitioner, and no simultaneous appointments
  for the same patient.
- An appointment may start at any available time. The user interface may suggest slots to
  make picking a time easier, without forcing a rigid grid onto the domain.
- Agenda views are the simplest ones that support the daily work.

### Check-in and automatic cancellation

- The patient stops at reception, which confirms their arrival.
- Without a confirmed check-in, the appointment is automatically cancelled **30 minutes**
  after its start time.
- If the patient warns they will be late, reception **suspends** that cancellation. If they
  never show up, the appointment is cancelled **24 hours** after its start time.
- Confirming the check-in prevents both automatic cancellations.
- This rule is provisional: once the attendance feature exists, a patient no-show will be
  recorded instead of cancelling the appointment.

## v2

v2 is built in phases, each one scoped here before it starts and closed on its own. The first,
the clinical record, is closed, and so are the second, the arrival queue, and the third, users
and security, the fourth, the patient's extra data, the fifth, reminders and the cancellation log,
and the sixth, web booking for patients, and the seventh, the cash desk. The eighth, registering
from the web, is in progress. Each next one is chosen from the planned evolution below, and its scope is written here before any code.

## v2, phase 1 — the clinical record (closed)

### An appointment is only a booking

An appointment says who is seen, by whom, when, for what reason and for how long, plus a short
note. What happens once the patient is at the practice — the arrival, the consultation, what
comes after — belongs somewhere else, and does not depend on the appointment: once the arrival
queue exists, patients will be seen without one.

In v1 the check-in lives on the appointment. It stays there until the arrival queue, which
brings a second way of arriving, takes the arrival over. Until then the clinical record asks
whether a practitioner is seeing a patient today without knowing where the answer comes from.

### Consultations

- A practitioner records a consultation of a patient they are seeing today: today that means one
  who has checked in on an appointment with them that day.
- A consultation holds free text about what was consulted, its prescriptions, the checks done
  during it and the orders issued in it. Prescriptions are free text; structure comes later.
- It is sent whole, once, on the day the patient is seen. Once sent it can be neither changed nor
  deleted. Keeping a draft to finish later is the user interface's business.
- A patient seen twice on the same day by the same practitioner has two consultations.
- Recording a consultation changes nothing on the appointment.
- Only practitioners write clinical content; reception writes none.

### Checks

A check is a procedure, not an encounter: what is done once and reported once, by one author. An
electrocardiogram is one check, whose report includes the heart rate the device measures; the
heartbeat heard with a stethoscope during the consultation is another.

- A practitioner records a check done to a patient they are seeing today: during a consultation,
  sent with it, or — as a precaution for an emergency — without one.
- Its result is known at once (a pulse of 72) or arrives later (a blood test). Free text today,
  meant to be given a structure later.
- A pending result is recorded by any practitioner who may read the patient's record — whoever
  did the check, or whoever analyses a sample. A check has one result, and it never changes: a
  wrong one is corrected with an addendum to the check, as a consultation is.
- Checks may one day come from other practices, through an anti-corruption layer; nothing in the
  model assumes a check was done here.

### Who reads the clinical record

A patient's clinical record is their consultations and their checks, from every practitioner of
the practice. A practitioner may read the whole of it when:

- they recorded a consultation of that patient, or did a check to them, within the access period
  — one year today, and meant to change without touching the code; each one starts it again; or
- they have an appointment with the patient today that is not cancelled, even before the patient
  arrives, so they can prepare the visit.

A future appointment grants nothing before its day. A patient last seen longer ago than the
access period is treated as a new one. Reception does not read the clinical record.

**Anything a user may not see is refused with 403**, here and across the whole application —
never answered as if it were empty.

### Screens

On a practitioner's agenda, "Empezar consulta" on today's appointments whose patient has checked
in. It opens a consultation view: the form, with the patient's clinical record beside it.

### Delivered in parts

1. Consultations and the clinical record, as above.
2. Addenda: a practitioner with access to the record — its author or any other — adds a note to
   a sent consultation, with its date and author, at any time. It is about the consultation as a
   whole, and addenda are not added to addenda. What was written never changes.
3. Checks, in four steps:
   - **3a** — checks with their result at once or later, as above; the clinical record becomes a
     module of its own.
   - **3b** — asking for access to a record: a practitioner of the practice who will report a
     pending check, and cannot read the record, asks for it — only for a pending check, so an
     automatic approval cannot open any record at will. Granted automatically for now, and
     recorded — who asked, for which patient and check, when, until when, why, how it was
     approved — so that who authorizes, and why, can be decided later. It lasts a configurable
     period, a year today. Whether a reason is required depends on the law, so it is a setting;
     required or not, nobody judges it. Every grant shows in the record. A list of the checks
     waiting for a result, showing only the patient and the check, lets whoever reports them ask
     for access. A grant opens the record; it does not let anyone record a consultation or a
     check.
   - **3c** — the order: a practitioner who needs a check they cannot do authorizes it, during a
     consultation for now. Whoever fulfils it does not matter: one check fulfils it, done by any
     practitioner who sees the patient. Its author may cancel it, with a reason. One fulfilled
     elsewhere stays open: that is none of the application's business, and an order coming from
     elsewhere — on paper, or from another practice — will need validating, later. It replaces the
     study-order item.
   - **3d** — "Mis órdenes": whoever issued orders follows them up — those still waiting (open,
     or done and waiting for the result), and those reported in the last 30 days, or all of them
     on asking. It shows where each stands, never what a result says. Marking a result as
     reviewed, a state of each practitioner's own, is left for later.
4. Attachments: files attached to a consultation — a photo — or to a check — an electrocardiogram
   trace, a laboratory's PDF — at the time or later, by any practitioner who may read the record.
   PDF, PNG or JPEG, up to 10 MB each. A file may be a check's whole result. Nothing attached is
   ever deleted; a file attached by mistake is said so in an addendum. Only practitioners attach
   files. Files attached to the record as a whole — a report the patient brings from elsewhere —
   come later, with the orders from elsewhere. The files live in the database today, and must be
   able to move to an object store.

Not in this phase: an admin role (it arrives with user administration), structure for each kind
of item, printing prescriptions and orders, extra patient data, marking results as reviewed,
orders and files coming from elsewhere.

## v2, phase 2 — the arrival queue (closed)

### What changes

Until now a patient was seen only with an appointment, and the arrival was a check-in on it. From
this phase on the patient arrives into a **queue**, with an appointment or without one, and the
queue — not the appointment — knows who arrived, who is waiting and who was seen. The appointment
goes back to being only a booking: it is kept, cancelled, or the patient was absent.

### Nobody is turned away for lack of an identity

The right to be seen comes before the paperwork: a patient who will not identify themselves is
still seen, and their clinical record is still kept.

- A patient who gives no name is registered under a random alias, such as `NN-4821`, that no
  other patient has. Reception replaces it with the real name and document when the patient gives
  them.
- Every patient carries whether their identity is **verified**: reception marks it once it has
  seen a document. Existing patients start unverified.
- Every change to a patient's identity is recorded — what it was, what it became, who changed it
  and when. The patient is always the same one: the clinical record points at them by an internal
  key that never changes, so changing the identity touches nothing clinical.

### Queues

- A queue is set up once and kept: a name, a consulting-room number, and the practitioners who
  serve it — one, for a practitioner's own room, or several, for a shared queue such as an
  emergency desk. What is kept is its shape; who is in it is each day's business.
- A practitioner serves **one queue at most**. Reception adds and removes practitioners without
  deleting the queue. A queue left with no practitioner stays open, until reception adds one or
  closes it.
- Reception opens and closes queues, and may reopen a closed one.
- A queue still open at the end of the day closes itself, unless reception turned that off for as
  long as the queue stays open.

### Getting into a queue, and being called

**Only reception puts patients into a queue.** A practitioner only calls the next patient and reads
the records of the patients they may call.

- A patient with an appointment today goes into the queue of that appointment's practitioner, and
  only that practitioner may call them. When the practitioner serves no queue, the patient cannot
  come in with that appointment.
- A patient without an appointment goes into the queue reception chooses. There is no limit to how
  many may wait.
- Reception may mark a patient as an **emergency**, when they come in or while they wait.
- A patient may wait in more than one queue at once: queues know nothing of each other, and being
  called in one changes nothing in another.
- The order is decided by one rule, which knows nothing about screens or modules:
  - emergencies first, then patients with an appointment, then patients without one;
  - emergencies in the order they were marked as such, and patients without an appointment in the
    order they arrived;
  - patients with an appointment by the later of their appointment's time and the time they
    arrived: whoever comes early waits for their time, and whoever comes late is placed by the
    time they came, behind appointments earlier than that, not by the time they had.
- "Llamar siguiente" calls the first patient in that order that the practitioner may call. The
  practitioner never chooses: choosing is the order's job. Calling opens the consultation, which is
  sent whole when it is finished, as before.
- A practitioner has one patient called at a time. "Terminar" says that patient was seen; only
  then, or after "No ingresó", may they call the next.
- "No ingresó" says the called patient did not come in. They are passed over at the next call only,
  and keep their place for the ones after. After the second time they are called again without
  coming in — three calls in all, a setting — they are taken out of the queue as **did not show
  up**. In a shared queue, whoever of its practitioners may call them calls them again.
- In a queue: **waiting → called → seen**. Reception marks a patient who **left without being seen**.
  Whoever is still waiting when the queue closes is **not seen**; a reason may come later. Reopening
  a queue does not bring them back: reception puts them in again if needed.
- When the practitioner of an appointment leaves the queue, reception may give the patient to
  another practitioner without losing their place. Doing so does not change the appointment. The
  reception screen shows every waiting patient that nobody can call, and why.
- A patient with an appointment stays kept for their practitioner, as an emergency too; reception
  may lift that, and then whoever serves the queue may call them.
- An appointment cancelled while its patient waits in the queue leaves them there, in their place
  and kept for nobody: they did arrive, and taking them out is reception's call.
- A practitioner's time off does not close their queue. The practitioner lets reception know,
  reception tells the waiting room — outside the application — and closes the queue when it sees
  fit.

### Absent instead of cancelled

- An appointment whose patient has not arrived 30 minutes after its start is **absent**, no longer
  cancelled.
- A late-arrival notice keeps the appointment from becoming absent until the end of the day, when
  it becomes absent if the patient still has not come. It gives no priority: a patient who warned
  is placed like any other late arrival. It stays on the appointment: the queue does not know who
  is coming late.
- An absent appointment stays absent: it is the record that the patient did not come. Reception
  may still put the patient into a queue, without an appointment or as an emergency.
- Appointments cancelled automatically before this phase stay cancelled: nothing told them apart
  from those cancelled by hand. The check-ins already recorded move into the queue's records.
- The time-off cascade keeps its rule: it cancels the appointments whose patient has not arrived,
  and reports the rest for manual handling. It now learns who arrived from the queue.

### Who reads and writes the clinical record

On top of the access of phase 1:

- The practitioners of a queue read the record of every waiting patient they may call. A patient
  kept for another practitioner is read only by that one.
- An appointment today, even an absent one, still lets its practitioner read the record.
- Only the practitioner who called the patient records that patient's consultations and checks
  that day, also once they were seen: a consultation is often written at the end of the day.
- **Every reading of a clinical record is recorded**: who read it, whose it is, when, and by which
  access. Nobody reads that log in this phase — not on a screen, not through the API. Admins will,
  when they exist.

### Screens

They update themselves as things happen, without reloading.

- **Reception:** every queue — setting them up, opening and closing them, bringing patients in with
  or without an appointment, marking emergencies and patients who left, giving a patient to
  another practitioner, and the warning about patients nobody can call.
- **Practitioner:** their queue and "Llamar siguiente".
- **Waiting room:** shows the queues, without patients' names. It will grow later.

### Delivered in parts

1. The read log of the clinical record, which already applies to today's access.
2. Patient identity: the alias, whether it is verified, and the history of its changes.
3. Absent instead of cancelled, with the check-in still on the appointment.
4. Setting up queues: their shape and their practitioners.
5. Queues at work, in two steps:
   - **5a** — sessions: opening, closing, reopening and closing by itself; patients without an
     appointment and emergencies; calling, "Terminar" and "No ingresó"; the screens of reception
     and of the practitioner; the clinical record learns who a practitioner is seeing from the
     queue as well.
   - **5b** — patients with an appointment: into their practitioner's queue, kept for them and
     placed by the time; giving them to another practitioner; the arrival leaves the appointment,
     and the absences and the time-off cascade learn who arrived from the queue.
6. Screens that update themselves, and the waiting-room screen.

Not in this phase: merging two records of the same patient, a reason for "not seen", reading the
read log, consulting rooms as something more than a number, a waiting room that calls patients by
name, warnings about coverage or electronic prescriptions for unverified patients, and consent to
be seen privately.

## v2, phase 3 — users and security (closed)

### What changes

Until now the users came from the development data, and the whole of reception shared one. From
this phase on every person who works at the practice has a user of their own, created by an
**admin**, so that whatever is recorded under a username is one person's doing. The admins also
read who read which clinical record, and who tried to reach one and was refused.

### Roles

- **Reception**, **practitioner**, **admin** and **waiting room**. A user has **one role**; a person
  who needs two — the owner who also sees patients — has two users.
- The **admin** administers users and reads the logs below, and nothing else: they do not do
  reception's work, and they read no clinical content. Clinical content is still written and read by
  practitioners only.
- The **waiting room** is the screen on the wall: it sees the waiting-room screen and nothing else,
  and goes straight to it on logging in.
- A user's role never changes. A user who needs another role is deactivated, and a new one created.

### Users

- The admin creates users: a username, a role, a password, and — for a practitioner — the
  practitioner they stand for, chosen among those with no active user. A practitioner has one active
  user at most, and a user's practitioner never changes. Practitioners themselves are still
  registered by reception.
- A user is **never deleted**: what they did is recorded under their username. The admin deactivates
  and reactivates them. A username never changes and is never used again.
- A deactivated user loses access **at once**, their open sessions included. Nothing they did or read
  is changed: the logs keep it as it was. Deactivating a practitioner's user changes nothing about the
  practitioner — their agenda, their appointments, the queues they serve.
- Every action of an admin on a user — created, deactivated, reactivated, password reset — is
  recorded, with who and when, and the admins read it.

### Passwords

- The admin sets a **temporary** password when creating a user or resetting theirs; the user must
  change it the next time they log in, before doing anything else.
- Anyone changes their own password by giving the current one.
- From 10 to 72 characters, with no rules about what they must contain.
- No email or reset link: the practice has no email for its staff in the application.

### The logs the admins read

- **Readings of the clinical records:** who read — the practitioner and their user —, whose record —
  the patient's name and document —, when, by which access, and which attached file. Never what was
  read. Filtered by patient, by practitioner and by dates. Readings recorded before this phase show
  the practitioner alone, since their user was not recorded.
- **Refused attempts:** a practitioner refused a record or an attached file they may not read — who,
  which patient, when, what —, and anyone refused a clinical route by their role — who, which route,
  when. Recorded even though nothing was handed over, to notice someone probing records that are not
  theirs.
- Seeing who read a patient's record says something about the patient; that is why only the admins
  read these logs.

### Development data

The users of the development data stay as they are, and an admin (`admin`) and a waiting-room user
(`sala`) are added; none of them has to change their password. How the first admin of a real
deployment is created is left for when there is one.

### Screens and API

Everything the admin does has a screen and a route in the API, as in the rest of the application.

### Delivered in parts

1. The admin and the users: creating, listing, deactivating and reactivating, resetting a password,
   each action recorded; the sessions of a deactivated user cut at once.
2. Passwords: changing one's own, and the change a temporary password forces.
3. The waiting-room user.
4. The admins read the readings of the clinical records.
5. Refused attempts, recorded and read by the admins.

Not in this phase: patients' accounts (web booking), several roles per user, a restricted reception
role, two-factor authentication, locking a user after failed logins, recording failed logins,
resetting a password by email, the first admin of a real deployment.

## v2, phase 4 — the patient's extra data (closed)

### What changes

Until now a patient was a name, maybe a document, and how far the practice knows who they are.
From this phase on reception also records when they were born, how to reach them and their health
insurance. The next phases build on it — reminders need to reach the patient, web booking their
email — but nothing of those phases is built here.

### What is recorded

- **Date of birth.** Not in the future, and not more than 130 years ago.
- **One phone number.** Digits, with an optional leading `+`; the spaces, dashes and brackets
  reception types are dropped, as they are from a document. From 8 to 15 digits. Whether it takes
  WhatsApp is left for the reminders.
- **One email.** A plain check of its form, at most 254 characters, compared without regard to
  case. It is **not unique** among patients: two brothers may carry their mother's. Whether the
  email of a web account is unique is decided with the web booking.
- **Whose each one is.** The phone and the email each say whose they are: the patient's own, or
  those of a person responsible for them — a mother, a father, a guardian, someone else — whose name
  and relation are recorded with it. A child has no phone of their own; an adolescent may have their
  own phone and use their mother's email, and separated parents may give one each, so each one has
  its owner. That person is not a patient nor anything else of their own: only a name and a relation
  beside the phone or the email.
- **Health insurance.** One at most: the name of the insurer, the member number and the plan, all
  free text, since each insurer has its own format; only their length is checked. A patient with none
  pays privately. The member number is the patient's and is kept; the token some insurers ask for is
  generated anew for each visit, so it is not the patient's and is not kept here.

**Nothing is required.** The patients already on the books start with none of it, and nobody has to
fill it in. A wrong value is refused where it is typed, and nothing more (D-38). A patient registered
under an alias may have all of it: someone who gives no name may still leave a relative's phone.

### Minors

The law gives minors a progressive autonomy (Código Civil y Comercial, art. 26): from 13 an
adolescent decides alone on treatments that are not invasive, and from 16 is treated as an adult in
the care of their own body. A minor who comes alone is seen, as anyone is (phase 2), so **the person
responsible for a minor is never required**. The consent a minor gives, alone or assisted, for a
medical act is clinical content, and not part of this phase.

### Who reads and writes it

- **Reception** reads and writes all of it.
- **Practitioners** read the health insurance and the date of birth — the patient's age — beside the
  clinical record and the consultation. They do not read how to reach the patient, and write none of
  it.
- **Admins** read none of it.

### Every change is kept

Every change to this data is recorded — what it was, what it became, who changed it and when — as a
patient's identity changes are. Reception reads that history, on the patient's screen and through the
API; nobody else does.

### Registering and booking at once

A new patient registered from the booking screen — with their contact, since that is where it is most
often taken, over the phone — and their appointment are saved in **one transaction**: a booking that
is refused leaves no patient behind (the postponed half of D-37).

### Delivered in parts

1. The date of birth and the contact — phone and email, and whose each one is —, with their history;
   practitioners read the patient's age beside the record.
2. The health insurance, with its history; practitioners read it beside the record.
3. Registering and booking in one transaction from the booking screen, with the contact.

Not in this phase: searching patients by phone, more than one phone or email, marking a phone as
WhatsApp, consent to be contacted, a list of insurers kept by the practice, more than one insurance,
the person responsible as a patient of their own, the consent of minors to medical acts, and anything
of the reminders, the cash desk or web booking.

## v2, phase 5 — reminders and the cancellation log (closed)

### What changes

Until now a cancelled appointment said only that it was cancelled: not who cancelled it, when, why,
nor whether reception did it or a practitioner's time off did. And the application reminded nobody of
their appointment. From this phase on every cancellation is recorded, each of the patient's channels
says whether it may be used to reach them, and the application reminds them of their appointment by
email a few days before.

### The cancellation log

- Every cancellation records **who** cancelled it, **when**, **how** — by hand at reception, or by a
  practitioner's time off —, **who asked** for it — the patient or the practice — and an optional
  **note**. A cancellation by time off was made by whoever recorded the time off, and asked by the
  practice.
- The cancellations are listed by the day they were made, showing the appointment's day beside it.
- Moving an appointment is not a cancellation, and it keeps no history of its previous times, as since
  v1. An absent appointment is not a cancellation either: it is the record that the patient did not
  come (phase 2).
- The appointments cancelled before this phase say that nothing was recorded of their cancellation:
  nothing tells who did it, nor whether it was by hand or automatic, and nothing is made up.
- **Reception** reads all of it: on each cancelled appointment, and in a list of cancellations filtered
  by dates and by practitioner.
- **Admins** read it too, to follow what the staff does, but **without the patient and without the
  note**: who cancelled, when, how, who asked, and the practitioner and time of the appointment. The
  note is reception's alone, since it may say something close to clinical — "cancels because she is
  in hospital".
- **Practitioners** do not read it.
- Patients still do not cancel by themselves: they call or come to the practice, reception cancels,
  and the log says the patient asked. Cancelling from the reminder waits for web booking.
- The application does not tell a patient that their appointment was cancelled: reception calls them,
  and recording a time off already lists the appointments it cancelled.

### Consent to be contacted

The data protection law does not require consent for data that come from a professional relationship
and are needed to carry it out (Ley 25.326, art. 5), and a reminder may be that. But that someone has
an appointment with a practitioner is health data, which the law treats as sensitive, so the practice
asks.

- **Each channel carries its own consent**: the phone one, the email another. Only reception records
  and withdraws it; practitioners and admins do not read it.
- A consent says **who gave it**: the patient, or — for a responsible person's channel — that person.
- The patients already on the books start with **no consent**: nobody is reminded until reception
  records it.
- Changing a channel's number, address or owner **drops its consent**: it was given for that number
  and that person.
- Every change to a consent is kept in the history of the patient's details (phase 4), with who and
  when.

### Adolescents

From 13 an adolescent decides alone on much of their own care (Código Civil y Comercial, art. 26), and
the confidentiality of what a patient tells the practice is one of their rights (Ley 26.529, art. 2 d),
also towards their parents. A reminder sent to a parent may tell them of a visit the adolescent keeps
to themselves.

- A reminder goes to a **responsible person's channel** of a patient **13 or older** only when the
  consent was given **by the patient themselves**. The same rule lets an adult who uses their
  daughter's email be reminded there, because they said so.
- For a patient under 13, the responsible person's consent is enough.
- A patient with no date of birth is taken as 13 or older when the channel is a responsible person's.
  Their own channel is used on their own consent, whatever their age.

### The reminders

- **By email**, and nothing else in this phase. The sending goes through a port, so WhatsApp or SMS
  will be one more adapter without touching the rules; whether a phone takes WhatsApp waits for that
  adapter.
- **When:** every day, from a configurable time — 10:00 by default —, the application reminds every
  booked appointment of the next days — 3 by default, configurable —, not today's, that has not been
  reminded yet. An appointment booked two days ahead is reminded at the next day's run. Each
  appointment is reminded once; one moved after its reminder is reminded again, with its new time.
- **Not sent** for an appointment that is no longer booked, or when the patient has no email, the
  email has no consent, or the adolescents' rule says no.
- **One attempt.** A failed sending is recorded, and reception sees it, to call the patient instead.
- **What it says**, in Spanish, since the patient reads it: the practice's name as a header, a
  greeting, the day, the time and the practitioner, and "to cancel or change it, call … or come to
  reception", with the practice's phone and address. The practice's name, address, phone and sender
  come from the configuration. It never says the reason for the appointment, which may be clinical.
  The practitioner's speciality and consulting room are not said either: the application does not know
  the first, and the second belongs to the queues. When a practitioner has both, both go in.
- **The greeting:** on the patient's own channel it names the patient; on a responsible person's, it
  names that person, and says whose appointment it is. A patient under an alias is greeted without a
  name and without anything that says their sex.

### The record of each reminder

- What happened to each appointment's reminder: sent, failed, or not sent and why, and when, on which
  channel.
- The address it went to is **not kept**, only a hash of it that cannot be turned back into it: it
  answers whether a reminder went to a given address. The text is not kept.
- **Reception** reads it for each appointment, in a list by the appointment's day, a failed one marked so
  it calls the patient; nobody else does.

### Screens and API

Everything reception and the admins do or read here has a screen and a route in the API, as in the rest
of the application.

### Delivered in parts

1. The cancellation log: who, when, how, who asked and a note; read by reception, and by the admins
   without the patient and the note.
2. Consent to be contacted, per channel, with who gave it and its history.
3. The reminders by email, with the adolescents' rule and the record of each one.

Not in this phase: reminders by WhatsApp or SMS, and marking a phone as WhatsApp; cancelling or
confirming from the reminder (web booking); telling the patient of a cancellation; a history of the
times an appointment was moved; a list of cancellation reasons; practitioners reading the cancellation
log; the practitioner's speciality and consulting room in the reminder.

## v2, phase 6 — web booking for patients (closed)

### What changes

Until now only the staff used the application: a patient called or came to the practice to book or
cancel, and the reminder told them to. From this phase on a patient has an **account** of their own,
with which they book, see and cancel their appointments and keep their contact and health insurance
up to date; a family books for its members from a **family group**; and anyone who gets a reminder
cancels the appointment from a link in it. The staff's application stays as it was: what a patient
does on the web shows in it, and nothing else changes for reception.

### The account

- **Reception opens it**, at the desk, for a patient whose identity is **verified** (phase 2) and who is
  **13 or older** — a patient with no date of birth gets none until reception records it. Reception
  types the account's email, and the patient gets a link there to activate it and choose their
  password. Nobody opens an account, nor registers a patient, from the web: a document the practice
  does not know is sent to reception.
- An account is always a patient's: a mother who is never seen at the practice is registered as a
  patient to have one. A patient has one account at most.
- **The patient logs in with their document** — its type and number — and their password, and never
  with the internal id. So the account's email does not have to be unique: a family may share one.
- **The account's email is its own**, apart from the patient's contact email (phase 4): it is confirmed
  by the activation, and it is used only for the account — the activation, recovering the password and
  confirming bookings —, never for reminders. Changing the contact email does not touch it, so reception
  changing a contact cannot hand the account to someone else. It changes only at reception, with the
  document in sight.
- From 10 to 72 characters, the staff's rule (phase 3). The patient chooses it on activating, so it is
  never temporary.
- **Five failed logins in a row lock the account for 15 minutes**, both settings: the document that
  logs in is easy to know, and the login faces the internet. The staff's users are not locked (phase 3).
- **A forgotten password** is recovered with a link to the account's email, valid for one hour. Asking
  for it says the same whether the document has an account or not. The staff still cannot recover theirs
  by email.
- **Reception administers the accounts**: on the patient's screen it sees whether they have one and in
  what state, opens it, sends the activation again, changes its email, and deactivates and reactivates
  it. Every action on an account — reception's and the patient's own, such as activating it or changing
  the password — is recorded with who and when, and reception reads it. The admins read none of it, as
  they read nothing of the patients (phase 4).

### What a patient sees and does with their account

- **Their appointments**: those ahead and those of the last 12 months, with the day, the time, the
  practitioner, the reason and the state — booked, cancelled or absent. A cancelled one says whether the
  patient or the practice asked for it; never reception's note, nor who cancelled it. The appointment's
  note is reception's and is not shown either.
- **Their data**: their name and document, which they do not change, and their contact — the phone and
  the email, and whose each one is —, the consent of each channel, and their health insurance, which
  they change with reception's rules: a new number, address or owner drops its consent (phase 5), and a
  consent the patient gives on the web is given by the patient. Every change goes to the history of the
  patient's details as made by the account. The name, the document and the date of birth stay
  reception's, since they are what verifying the identity looked at.
- **Nothing clinical**: no consultations, checks, orders nor files. Clinical content is still written and
  read by practitioners only (phase 1); a copy of the record is asked for at the practice (Ley 26.529,
  art. 14).

### Booking

- **Reception marks which reasons may be booked on the web**, practitioner by practitioner; none may at
  first, so nothing is offered until reception says so.
- **From tomorrow to 30 days ahead**, both settings. Today's appointments are still reception's.
- **Only at the times the agenda suggests**, not at any free time as reception may.
- **Three appointments ahead at most per patient**, counting every booked one, whoever booked it. The
  limit binds the web only: reception books beyond it.
- The rules of booking are the same as reception's: no overlapping appointments for a practitioner, none
  at once for a patient.
- **An email confirms the booking** to the account that booked: the practice, the day, the time, the
  practitioner, and the patient's name when it was booked for another member of the group, with a link
  to cancel it. Never the reason, as in the reminder.
- **The appointment keeps where it was booked**: at reception or on the web. The agenda and the
  patient's appointments mark the web ones, and reception is told of nothing else.

### Cancelling

- **On the web**, a patient cancels any of their booked appointments — those reception booked too — up
  to **24 hours** before it, a setting; later, they call. Moving an appointment is not offered: they
  cancel it and book again.
- **From the link of an email** — the reminder, or the confirmation of a booking —, without logging in,
  so a patient with no account cancels too, and so does whoever got the reminder on their channel, such
  as a child's mother. The link carries nothing personal in the address, only an opaque token, which the
  application keeps as a hash. It opens a page with the day, the time and the practitioner — what the
  email already says — and a button to cancel. It works until the same 24 hours before, and no more once
  the appointment is moved or cancelled: a moved appointment is reminded again, with a new link.
- **The reminder says so**: to cancel, the link, or call or come to reception, as before for changing it.
- **The cancellation log** (phase 5) says the patient asked, and how: from the web, made by the account
  that cancelled — which may be another member of the family group —, or from an email's link, made by
  whoever held it. Reception reads it whole, as it does now; the admins read "patient (web)" and nothing
  that tells who the patient is.
- There is no confirming the appointment: nothing would read it.

### The family group

So that a family books from one account: a mother for her children, a couple for each other.

- **Reception forms it**, at the desk. Its members are patients. An adult joins only present and
  agreeing, which shares their appointments with the group. A child under 13 is added at the request of a
  member who is their parent or guardian, once reception has seen what proves it. Every change to a group
  is recorded, with who and when.
- **A patient may be in several groups**: the child of separated parents in each one's (Código Civil y
  Comercial, art. 641), an adult in their partner's and in their parents'.
- **What a member does for the others**: an adult member with an account sees the appointments of every
  member who shares theirs, as their own above, books for them and cancels them — a partner for a child
  that is not theirs too, as the daily acts of the child's upbringing (CCyC, art. 673). A member under 18
  acts only for themselves. Nobody reads nor changes another member's data: each changes only their own.
- **Children under 13 share always**: their parents act for them (CCyC, art. 26).
- **From 13, the adolescent decides.** They stay in the group, but from their thirteenth birthday their
  appointments are **private** — the group neither sees, books nor cancels them, those booked earlier
  included — until they choose to share them, from their own account or at reception; and they may stop
  sharing whenever they want. It is the reminders' rule (phase 5): from 13 an adolescent decides alone on
  much of their own care (CCyC, art. 26), and keeps their confidentiality towards their parents (Ley
  26.529, art. 2 d; Ley 26.061, art. 10), so nothing is shown to their family until they say so.
- **At 18 nothing changes**: what they chose as an adolescent stands, as an adult's agreement does.
- **Any member of 13 or older leaves** the group, or stops sharing, from the web or at reception;
  reception takes members out.

### Screens and API

The patients have screens of their own, in Spanish, with their own login and header, apart from the
staff's; and a route of their own in the API, the contract of the patients' single-page application to
come. As every screen, they are thin and meant to be thrown away (D-02).

### Delivered in parts

1. The account: reception opens it, the patient activates it by email, logs in and sees their
   appointments; reception administers it, and every action is recorded.
2. The patient's data: their contact, the consent of each channel and their health insurance, changed
   from the account.
3. Booking on the web: the reasons reception marks, from tomorrow to 30 days, at the suggested times,
   three at most; the email that confirms it; the web's appointments marked as such.
4. Cancelling, from the web and from the link of the reminder and of the confirmation, in the
   cancellation log.
5. The family group, in two: reception forms it, children and adults, with who shares their
   appointments (5a); the members book, cancel and see each other's appointments on the web, with the
   adolescents' privacy (5b).
6. Recovering the password by email, and locking the account after five failed logins.

Not in this phase: registering a patient, or opening an account, from the web; an account for someone
who is not a patient; moving an appointment from the web; confirming an appointment; booking today's
appointments on the web; anything clinical for patients, a copy of the record included; changing the
name, the document or the date of birth from the web; telling reception of what patients do; recording
who booked each appointment beyond where; recovering a password by email and locking users for the
staff; two-factor authentication; telling the patient of a cancellation the practice made; and anything
of the cash desk or of reminders by WhatsApp or SMS.

## v2, phase 7 — the cash desk (closed)

### What changes

Until now the application knew nothing of money: whoever was seen paid, or not, outside of it. From this
phase on **reception charges each visit before the patient goes into a queue**, records every charge,
and closes the day's cash desk. It grows in the order the money does at the desk: first a private
patient paying in cash, then the other ways of paying, and last the visits covered by a health insurance.

### A visit is charged before it is seen, except an emergency

- **A visit is each time reception puts a patient into a queue** (phase 2). A patient waiting in two
  queues makes two visits, and pays two. Giving the patient to another practitioner of the queue, or
  calling them again after "No ingresó", is the same visit.
- **Reception records the charge when it puts the patient into the queue**, in the same step: without
  it the patient does not go in. It is the only way into a queue, so nobody is seen unpaid by mistake.
- **An emergency goes in without being charged.** Its charge is left pending, and reception records it
  later, or never. The duty to assist comes before the payment in an emergency (Ley 26.529, art. 2 a;
  Código Penal, art. 108); outside of one, a private practice may ask to be paid first.
- **Nobody is asked who they are to pay** (phase 2): a patient under an alias pays as anyone does.
- The visits already in a queue when this phase arrives are not charged.

### A charge

- The **amount** reception types — there is no price list —, in pesos, greater than zero, with
  two decimals at most; the **means** of payment; who recorded it and when, by the `Clock`.
- **It is never changed nor deleted.** What went wrong is undone with a refund (part 1b) and charged
  again, so the day's cash desk always adds up to what moved.
- **A refund** is a movement of its own, apart from the charge it gives back: its amount, up to what is
  left of that charge, who recorded it, when, and why. A patient who paid and was not seen — who left
  without being seen, did not show up, or was still waiting when the queue closed — is owed their money
  back (Ley 24.240); the application never decides it alone: reception records it.

### The day's cash desk

- **The day's movements** — charges and refunds, with the patient, the means, the amount, who recorded
  each and when — and their **totals**: charged, refunded, and what is left, by means of payment and by
  who recorded them. Only cash counts for what should be in the drawer.
- **The pending visits**: the emergencies not charged nor covered yet.
- **The covered visits** (part 3): those covered that day, which move no money, listed apart so reception can
  set right a coverage marked by mistake.
- **Reception** reads and writes all of it. **Admins** read the day's cash desk, its movements and
  totals, without the patients: they read nothing of the patients (phase 4). **Practitioners** read
  none of it.

### Ways of paying, and the health insurance

- **Part 1** knows one means of payment: **cash**, and one way of paying: **privately**.
- **Part 2** adds the other means: debit card, credit card, bank transfer and QR. The day's cash desk
  separates them.
- **Part 3** adds the visits **covered by a health insurance** — an obra social or a prepaid plan —:
  the patient's insurance (phase 4), the **token** the insurer asks for that visit, which is the visit's
  and not the patient's, and a **copay** charged by any means of part 2, or none.

### A visit covered by a health insurance

- **Only a patient with an insurance recorded and a verified identity** (phases 2 and 4) goes in covered:
  the insurer validates who the patient is. Anyone else pays privately.
- **The insurer is asked outside the application**, on its own system or app, before the patient goes in;
  if it says no, the patient pays privately, and nothing of it is recorded. The application keeps the
  outcome: that the visit is covered, by which insurer, member number and plan as they were that day.
- **The token is optional.** The practice keeps no list of insurers (phase 4), so the application cannot
  know which ask for one: the insurer's check says so, and reception types it when it does.
- **A copay**, when the insurer asks for one, is a charge as any other — any means, refunded the same way
  —, and counts in the day's cash desk. A covered visit with no copay moves no money, so it is not among the
  day's movements nor in its totals: those are money only. Reception's day lists the visits covered that day
  apart, as it lists the pending ones.
- **An approved visit stays covered.** Once the insurer approved it, it cannot reject it at the desk; what
  it may discount later, auditing the month's billing, belongs to billing the insurers, not to this phase.
- An emergency goes in uncharged, as in part 1, and reception records later whether it was covered or
  paid privately.
- **A visit marked covered by mistake** is set right as the charges are: nothing is changed nor deleted.
  Reception withdraws the coverage, saying why, once its copay, if any, is given back whole, and charges the
  visit privately.

### The invoice

The application **records** what is charged; it does not issue the invoice, which ARCA requires and is
still made outside of it, as today. Nothing printed out of it is an invoice.

### Delivered in parts

1. Charging privately in cash, in two steps:
   - **1a** — the charge when the patient goes into a queue, an emergency's pending charge, and the
     day's cash desk.
   - **1b** — refunds, as movements of their own.
2. The other means of payment: debit, credit, transfer and QR.
3. The visits covered by a health insurance: the token and the copay, and withdrawing a coverage
   marked by mistake.

Not in this phase: issuing invoices with ARCA; billing the insurers each month; a price list; an opening
float or counting the drawer at the close; printing receipts; charging a visit the patient never came
to; practitioners reading the cash desk; and changing or deleting a charge.

## v2, phase 8 — registering from the web

### What changes

Until now only reception registered a patient and opened their account (phase 6): someone the practice
did not know had to call or come before booking on the web. From this phase on **an adult registers
themselves on the web, and their children under 13**, opens their account and books, and reception
verifies who they are when they come.

### Who registers, and what

- **An adult, 18 or older, registers themselves**: their name, their document — its type and number,
  required, since the account logs in with it —, their date of birth, and the account's email and
  password, with the rules of phase 6. A phone is optional.
- **And, if they want, their children under 13**: each one's name, document and date of birth, and the
  relation — mother, father or guardian. An adolescent from 13 is still registered at reception, where
  they decide on their own privacy (phase 6).
- **The registration is confirmed from a link to the account's email**, as an activation is; until then
  nobody is registered. A registration never confirmed is forgotten after a while, a setting.
- **A document the practice already knows** — the adult's or a child's — opens nothing: the screen says
  the same as for any registration, and the email asks them to come to reception. Nobody learns from the
  web whether someone is a patient (D-79).

### Unverified until reception sees them

- **Everyone registered on the web is a patient whose identity is not verified** (phase 2), and the
  patient's screen says they registered on the web. A child's relation with the adult is **not proved**
  either.
- **Until reception verifies them, each one has one appointment ahead at most**, booked on the web, by the
  rules of phase 6: the reasons open to the web, from tomorrow to 30 days, at the suggested times. Reception
  books beyond it, as always.
- **The adult acts for their children** — sees, books and cancels their appointments — in a family group
  the registration forms, as a parent does in phase 6, with the relation marked as not proved.
- **Reception verifies them at the desk**: the identity with the document in sight, as in phase 2, and the
  relation with what proves it, as in phase 6. From then on the usual rules apply: three appointments ahead,
  and the group as any other.
- **Nothing else waits on it**: a patient registered on the web is seen as anyone is, verified or not
  (phase 2), and goes into a queue covered by an insurance only once verified (phase 7).

### Accounts for someone who is not a patient

An account is still a patient's (D-79): a mother who is never seen registers as a patient with no visits,
from the web now, or at reception as before. There is no account of another kind.

### Delivered in parts

1. An adult registers themselves on the web, confirms the email, and books one appointment ahead until
   reception verifies them.
2. Their children under 13, registered with them or later from their account, in a family group with the
   relation not proved, and one appointment ahead each until reception verifies them.

Not in this phase: adolescents registering themselves; registering someone else than one's children;
proving the identity remotely; merging a web registration with a record the practice had under another
document or none (D-60); moving an appointment from the web; asking for a copy of the clinical record; a
captcha or any check against bots beyond the email's confirmation.

## Planned evolution (DO NOT implement yet)

Documented so today's decisions do not close doors, not to bring code forward. The next phases of
v2 are chosen from here.

1. **Arrival queue**: v2, phase 2, above — closed. **Users and security**: v2, phase 3, above — closed.
   **Extra patient data**: v2, phase 4, above — closed. **Reminders and the cancellation log**: v2,
   phase 5, above — closed. **Web booking**: v2, phase 6, above — closed. **Cash desk**: v2, phase 7,
   above — closed.
2. **Later on**: attendance with more states, structured clinical items, and printing.
3. **Reminders by other channels**: WhatsApp or SMS, each one more adapter behind the reminders'
   port, with whether a phone takes WhatsApp; the practitioner's speciality and consulting room in the
   reminder, once the application knows them.
4. **Cash desk, further on**: issuing invoices with ARCA, billing the insurers each month, a price
   list, printing receipts.
5. **Web booking, further on**: registering from the web is v2, phase 8, above — in progress. Then:
   - **moving an appointment from the web**, with the rules of booking and cancelling — up to 24 hours
     before, to a suggested time from tomorrow to 30 days ahead, with the same practitioner and reason —,
     for the family group too;
   - **asking for a copy of the clinical record from the web** (Ley 26.529, art. 14): the patient asks
     from their account, the application keeps the 48 hours it must be handed in, and the practice hands
     it authenticated. Reading the record online is not offered: clinical content stays the
     practitioners' (D-51).
6. **Future user interface**: two separate single-page applications, one for
   reception/practitioners and one for patients. This is why the REST API is the main
   contract from v1 onwards. The cash desk's screens there ask reception to confirm an amount a
   second time when it is very large, before sending it: the server only refuses what goes above
   its ceiling (D-87), so an amount with one zero too many that stays below it goes through.
