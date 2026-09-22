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

- Internal identifier, full name, optional national ID (DNI).
- The national ID is **not** the internal identifier. When given, it is unique.
- Patients can be registered without a national ID.
- Several patients may share the same name.
- There is no patient file and no login account.

### Practitioners and availability

- A usual weekly schedule, breaks and time off. Reception configures all of it.
- One practitioner per consulting room: no shared resources to manage.
- Reception records a practitioner's time off for a whole day or for a time range. The
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

## Planned evolution (DO NOT implement yet)

Documented so today's decisions do not close doors, not to bring code forward.

1. **Patient file**: an internal record the practice reads, showing the appointment
   history. This is why v1 keeps every appointment, cancelled ones included.
2. **Arrival queue** per consulting room/practitioner: accepts walk-in patients and gives
   priority to those with an appointment. It absorbs the difference between the reserved
   time and the real pace of the practice. Until it exists, reception registers a walk-in
   by creating a same-day appointment.
3. **Later on**: clinical history inside the file, attendance with more states, reminders or
   a cancellation log, extra patient data (phone, health insurance) and web booking.
4. **Web booking**: an account with login, linked to the internal file. It requires a
   national ID or another form of identification, and **never** uses the internal id to
   identify the patient.
5. **Future user interface**: two separate single-page applications, one for
   reception/practitioners and one for patients. This is why the REST API is the main
   contract from v1 onwards.
