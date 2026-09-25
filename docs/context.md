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

## Scope of v2 — the clinical record

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
- A consultation holds free text about what was consulted, a list of items and the checks done
  during it. Each item has a kind — a prescription, or an order for a study (an X-ray, a Doppler
  ultrasound…) here or elsewhere — and free text. Structure comes later, one kind at a time.
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
  did the check, or whoever analyses a sample. A check has one result, and it never changes.
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
   - **3b** — asking for access to a record: a practitioner of the practice who will do or report a
     check, and cannot read the record, asks for it. Granted automatically for now, and recorded —
     who asked, for which patient, when, why, who granted it — so that who authorizes, and why,
     can be decided later. It lasts a configurable period. A list of the checks waiting for a
     result, showing only the patient and the check, lets whoever reports them ask for access.
   - **3c** — the order: a practitioner who needs a check they cannot do authorizes it. Whoever
     fulfils it, here or at another practice, does not matter. It replaces the study-order item.
   - **3d** — the lists of what is pending for whoever ordered.
4. Attachments: an electrocardiogram trace, a report from elsewhere.

Not in v2: an admin role (it arrives with user administration), structure for each kind of
item, printing prescriptions and orders, extra patient data.

## Planned evolution (DO NOT implement yet)

Documented so today's decisions do not close doors, not to bring code forward.

1. **Arrival queue** per consulting room/practitioner: accepts walk-in patients and gives
   priority to those with an appointment. It absorbs the difference between the reserved
   time and the real pace of the practice, and it takes the arrival — check-in, late-arrival
   notice — over from the appointment. Until it exists, reception registers a walk-in by
   creating a same-day appointment.
2. **Later on**: attendance with more states, reminders or a cancellation log, extra patient
   data (phone, health insurance), structured clinical items, printing, and web booking.
3. **Web booking**: an account with login, linked to the patient's record. It requires an
   identity document, and **never** uses the internal id to identify the patient.
4. **Future user interface**: two separate single-page applications, one for
   reception/practitioners and one for patients. This is why the REST API is the main
   contract from v1 onwards.
