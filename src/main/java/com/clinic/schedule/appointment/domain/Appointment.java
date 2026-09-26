package com.clinic.schedule.appointment.domain;

import java.time.Duration;
import java.time.LocalDateTime;

import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.exception.NotTheAppointmentDayException;

/**
 * An appointment in the agenda: a patient, a practitioner, the slot it takes up, the reason
 * it was requested and an optional note — plus what happens on the day: whether the patient
 * checked in, and whether reception was warned they would be late.
 *
 * <p>Plain domain object, with no persistence or framework annotations: the adapter that
 * stores it is the one that knows about JPA.
 *
 * <p>It is immutable. Every operation returns a new appointment instead of changing this one,
 * so a rule that says no cannot leave a half-modified object behind, and every field can stay
 * final.
 *
 * <p>What this class decides on its own is what depends on itself alone: its shape, what can
 * happen to it in each state, and when its patient is absent. Whether a slot collides with somebody
 * else's appointment needs the rest of the agenda, so it is decided by {@link BookingService}.
 *
 * <p>The reason is carried as text: a snapshot of what was booked, together with the
 * duration, so renaming a reason later does not rewrite history (D-29).
 */
public class Appointment {

    /**
     * Without a check-in, the patient is absent this long after the appointment should have
     * started, unless reception was warned they would be late: then at the end of its day (D-61).
     */
    static final Duration GRACE_PERIOD = Duration.ofMinutes(30);

    /** As long as the column that stores it (D-38). */
    static final int MAX_NOTE_LENGTH = 500;

    private final AppointmentId id;
    private final PatientId patientId;
    private final PractitionerId practitionerId;
    private final TimeSlot slot;
    private final String reason;
    private final String note;
    private final AppointmentStatus status;
    private final LocalDateTime checkedInAt;
    private final boolean lateArrivalNotice;

    private Appointment(AppointmentId id, PatientId patientId, PractitionerId practitionerId, TimeSlot slot,
            String reason, String note, AppointmentStatus status, LocalDateTime checkedInAt,
            boolean lateArrivalNotice) {
        this.id = id;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.slot = slot;
        this.reason = reason;
        this.note = note;
        this.status = status;
        this.checkedInAt = checkedInAt;
        this.lateArrivalNotice = lateArrivalNotice;
    }

    /**
     * A brand new appointment. The id comes from the caller so that a retried request can
     * reuse it and not book twice; see {@link AppointmentId}.
     *
     * <p>Restricted to the domain package on purpose: a valid appointment also depends on
     * the rest of the agenda, so the way in is {@link BookingService#book}.
     */
    static Appointment book(AppointmentId id, PatientId patientId, PractitionerId practitionerId, TimeSlot slot,
            String reason, String note) {
        requireInvariants(id, patientId, practitionerId, slot, reason, note);
        return new Appointment(id, patientId, practitionerId, slot, reason.strip(), note, AppointmentStatus.BOOKED,
                null, false);
    }

    /**
     * An appointment read back from storage, which is what the persistence adapter uses. It
     * differs from {@link #book} in that the state comes from the row instead of being new.
     *
     * <p>It checks the same invariants, so there is no way to obtain an invalid appointment:
     * the aggregate does not trust the caller to have been careful. That is safe because the
     * rules that may change over time — overlap, availability — live outside the aggregate.
     * What is checked here is structural, and every stored appointment has always satisfied
     * it.
     *
     * <p>It is public because the adapter lives in another package and Java cannot export a
     * method to one package only. Using it to bypass {@link BookingService} is possible and
     * deliberate, the same way writing raw SQL would be; see D-15.
     */
    public static Appointment restore(AppointmentId id, PatientId patientId, PractitionerId practitionerId,
            TimeSlot slot, String reason, String note, AppointmentStatus status, LocalDateTime checkedInAt,
            boolean lateArrivalNotice) {
        requireInvariants(id, patientId, practitionerId, slot, reason, note);
        if (status == null) {
            throw new IllegalArgumentException("A stored appointment needs a status");
        }
        return new Appointment(id, patientId, practitionerId, slot, reason.strip(), note, status, checkedInAt,
                lateArrivalNotice);
    }

    private static void requireInvariants(AppointmentId id, PatientId patientId, PractitionerId practitionerId,
            TimeSlot slot, String reason, String note) {
        if (id == null || patientId == null || practitionerId == null || slot == null) {
            throw new IllegalArgumentException("An appointment needs an id, a patient, a practitioner and a slot");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An appointment needs a reason");
        }
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("A note is at most " + MAX_NOTE_LENGTH + " characters");
        }
    }

    /**
     * The same appointment, cancelled. It is kept as a record and never deleted, because the
     * patient file will show it.
     *
     * <p>Cancelling twice is rejected instead of ignored: it means whoever asked was looking
     * at a stale agenda. An appointment whose patient already arrived can still be cancelled:
     * that is the manual handling the context asks for when a practitioner is absent.
     */
    public Appointment cancelled() {
        requireBooked("cancelled");
        return new Appointment(id, patientId, practitionerId, slot, reason, note, AppointmentStatus.CANCELLED,
                checkedInAt, lateArrivalNotice);
    }

    /**
     * The same appointment on another slot. Version 1 keeps no history of the previous date.
     *
     * <p>A notice that the patient would be late was about the old time, so it does not carry
     * over. An appointment whose patient already arrived cannot be moved: the arrival belongs
     * to this time, not to another.
     *
     * <p>Restricted to the domain package like {@link #book}, because the new slot has to be
     * checked against the agenda: the way in is {@link BookingService#reschedule}.
     */
    Appointment movedTo(TimeSlot newSlot) {
        requireBooked("rescheduled");
        requireNotCheckedIn("rescheduled");
        if (newSlot == null) {
            throw new IllegalArgumentException("An appointment needs a slot");
        }
        return new Appointment(id, patientId, practitionerId, newSlot, reason, note, status, null, false);
    }

    /**
     * The same appointment, with the patient's arrival confirmed by reception. From then on it
     * is never marked absent (D-61).
     *
     * <p>Only on the day of the appointment: an arrival on another day is a mistake, most likely
     * the wrong line of the agenda (D-38).
     */
    public Appointment checkedIn(LocalDateTime at) {
        requireBooked("checked in");
        requireNotCheckedIn("checked in");
        if (at == null) {
            throw new IllegalArgumentException("A check-in needs the time it happened");
        }
        requireTheDayOfTheAppointment(at, "checked in");
        return new Appointment(id, patientId, practitionerId, slot, reason, note, status, at,
                lateArrivalNotice);
    }

    /**
     * The same appointment, after the patient warned they would be late: instead of 30 minutes,
     * it waits until the end of its day before the patient is absent (D-06, D-61). It gives no
     * priority: whoever warned is placed like any other late arrival.
     *
     * <p>Rejected once the patient arrived, since there is nothing left to wait for, and
     * rejected a second time like any other repeated operation. Only on the day of the
     * appointment, like the check-in it stands in for (D-38).
     */
    public Appointment withLateArrivalNotice(LocalDateTime now) {
        requireBooked("given more time");
        requireNotCheckedIn("given more time");
        requireTheDayOfTheAppointment(now, "given more time");
        if (lateArrivalNotice) {
            throw new BookingRuleViolationException("Reception was already warned that this patient will be late");
        }
        return new Appointment(id, patientId, practitionerId, slot, reason, note, status, checkedInAt, true);
    }

    /**
     * Whether the patient never arrived and the wait is over: 30 minutes after the start, or at
     * the midnight that ends its day when reception was warned. Never true once the patient
     * checked in, nor for an appointment that is no longer booked.
     */
    public boolean isDueForAbsence(LocalDateTime now) {
        if (!isBooked() || isCheckedIn()) {
            return false;
        }
        LocalDateTime due = lateArrivalNotice
                ? slot.startsAt().toLocalDate().plusDays(1).atStartOfDay()
                : slot.startsAt().plus(GRACE_PERIOD);
        return !now.isBefore(due);
    }

    /**
     * The same appointment, with its patient absent: the record that they did not come, which
     * replaces the automatic cancellation of v1 (D-61). It holds its slot no more, and nothing
     * happens to it afterwards: a patient who comes after all is seen some other way.
     *
     * @throws BookingRuleViolationException when the wait is not over, the patient arrived, or
     *         it is no longer booked
     */
    public Appointment markedAbsent(LocalDateTime now) {
        requireBooked("marked absent");
        requireNotCheckedIn("marked absent");
        if (!isDueForAbsence(now)) {
            throw new BookingRuleViolationException("The patient of this appointment may still come");
        }
        return new Appointment(id, patientId, practitionerId, slot, reason, note, AppointmentStatus.ABSENT,
                checkedInAt, lateArrivalNotice);
    }

    private void requireTheDayOfTheAppointment(LocalDateTime now, String action) {
        if (!now.toLocalDate().equals(slot.startsAt().toLocalDate())) {
            throw new NotTheAppointmentDayException(action, slot.startsAt().toLocalDate());
        }
    }

    private void requireBooked(String action) {
        if (status == AppointmentStatus.CANCELLED) {
            throw new BookingRuleViolationException("A cancelled appointment cannot be " + action);
        }
        if (status == AppointmentStatus.ABSENT) {
            throw new BookingRuleViolationException("An appointment whose patient was absent cannot be " + action);
        }
    }

    private void requireNotCheckedIn(String action) {
        if (isCheckedIn()) {
            throw new BookingRuleViolationException("An appointment whose patient already arrived cannot be " + action);
        }
    }

    public boolean isBooked() {
        return status == AppointmentStatus.BOOKED;
    }

    public boolean isCheckedIn() {
        return checkedInAt != null;
    }

    /** True when both are the same appointment. Identity exists from creation, so this is
     * always answerable. */
    public boolean isSameAs(Appointment other) {
        return id.equals(other.id);
    }

    public AppointmentId id() {
        return id;
    }

    public PatientId patientId() {
        return patientId;
    }

    public PractitionerId practitionerId() {
        return practitionerId;
    }

    public TimeSlot slot() {
        return slot;
    }

    public String reason() {
        return reason;
    }

    public String note() {
        return note;
    }

    public AppointmentStatus status() {
        return status;
    }

    /** When reception confirmed the arrival, or null if the patient has not arrived. */
    public LocalDateTime checkedInAt() {
        return checkedInAt;
    }

    /** Whether reception was warned the patient would be late. */
    public boolean hasLateArrivalNotice() {
        return lateArrivalNotice;
    }
}
