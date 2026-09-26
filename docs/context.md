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
the clinical record, is closed. The second, the arrival queue, is being built. Each next one is
chosen from the planned evolution below, and its scope is written here before any code.

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

## v2, phase 2 — the arrival queue

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

## Planned evolution (DO NOT implement yet)

Documented so today's decisions do not close doors, not to bring code forward. The next phases of
v2 are chosen from here.

1. **Arrival queue**: being built as v2, phase 2, above.
2. **Later on**: attendance with more states, reminders or a cancellation log, extra patient
   data (phone, health insurance), structured clinical items, printing, and web booking.
3. **Web booking**: an account with login, linked to the patient's record. It requires an
   identity document, and **never** uses the internal id to identify the patient.
4. **Future user interface**: two separate single-page applications, one for
   reception/practitioners and one for patients. This is why the REST API is the main
   contract from v1 onwards.
