package com.clinic.schedule.appointment.domain;

/**
 * An appointment in the agenda: a patient, a practitioner, the slot it takes up, the reason
 * it was requested and an optional note.
 *
 * <p>Plain domain object, with no persistence or framework annotations: the adapter that
 * stores it is the one that knows about JPA.
 *
 * <p>It is immutable. Cancelling or moving it returns a new appointment instead of changing
 * this one, so a rule that says no cannot leave a half-modified object behind, and every
 * field can stay final.
 *
 * <p>What this class decides on its own is what depends on itself alone: that it has a
 * patient, a practitioner, a slot and a reason, and that nothing happens to it once it is
 * cancelled. Whether a slot collides with somebody else's appointment needs the rest of the
 * agenda, so it is decided by {@link BookingService}.
 *
 * <p>The reason is carried as text. It belongs to the practitioner module, which owns the
 * list of reasons and their durations; whether this should become a reference is revisited
 * when that module exists.
 */
public class Appointment {

    private final AppointmentId id;
    private final PatientId patientId;
    private final PractitionerId practitionerId;
    private final TimeSlot slot;
    private final String reason;
    private final String note;
    private final AppointmentStatus status;

    private Appointment(AppointmentId id, PatientId patientId, PractitionerId practitionerId, TimeSlot slot,
            String reason, String note, AppointmentStatus status) {
        this.id = id;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.slot = slot;
        this.reason = reason;
        this.note = note;
        this.status = status;
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
        requireInvariants(id, patientId, practitionerId, slot, reason);
        return new Appointment(id, patientId, practitionerId, slot, reason.strip(), note, AppointmentStatus.BOOKED);
    }

    /**
     * An appointment read back from storage, which is what the persistence adapter uses. It
     * differs from {@link #book} in one thing only: the state comes from the row instead of
     * being {@code BOOKED}.
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
            TimeSlot slot, String reason, String note, AppointmentStatus status) {
        requireInvariants(id, patientId, practitionerId, slot, reason);
        if (status == null) {
            throw new IllegalArgumentException("A stored appointment needs a status");
        }
        return new Appointment(id, patientId, practitionerId, slot, reason.strip(), note, status);
    }

    private static void requireInvariants(AppointmentId id, PatientId patientId, PractitionerId practitionerId,
            TimeSlot slot, String reason) {
        if (id == null || patientId == null || practitionerId == null || slot == null) {
            throw new IllegalArgumentException("An appointment needs an id, a patient, a practitioner and a slot");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An appointment needs a reason");
        }
    }

    /**
     * The same appointment, cancelled. It is kept as a record and never deleted, because the
     * patient file will show it.
     *
     * <p>Cancelling twice is rejected instead of ignored: it means whoever asked was looking
     * at a stale agenda.
     */
    public Appointment cancelled() {
        requireBooked("cancelled");
        return new Appointment(id, patientId, practitionerId, slot, reason, note, AppointmentStatus.CANCELLED);
    }

    /**
     * The same appointment on another slot. Version 1 keeps no history of the previous date.
     *
     * <p>Restricted to the domain package like {@link #book}, because the new slot has to be
     * checked against the agenda: the way in is {@link BookingService#reschedule}.
     */
    Appointment movedTo(TimeSlot newSlot) {
        requireBooked("rescheduled");
        if (newSlot == null) {
            throw new IllegalArgumentException("An appointment needs a slot");
        }
        return new Appointment(id, patientId, practitionerId, newSlot, reason, note, status);
    }

    private void requireBooked(String action) {
        if (status != AppointmentStatus.BOOKED) {
            throw new BookingRuleViolationException("A cancelled appointment cannot be " + action);
        }
    }

    public boolean isBooked() {
        return status == AppointmentStatus.BOOKED;
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
}
