package com.clinic.schedule.reminder.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What happened to the reminder of an appointment, once (D-78): sent, failed, or not sent and why, on
 * which channel and when. It keeps the appointment's time it was about, so an appointment moved after
 * its reminder is reminded again, and its patient and practitioner, so it can be listed without asking
 * for the appointment.
 *
 * <p>The address it went to is here only while it is being recorded: storage keeps a hash of it and
 * never the address, nor the text.
 *
 * @param address the address it went to, or was tried at; null when not sent, and once stored
 * @param reason why it was not sent; null when it was, or was tried
 */
public record Reminder(UUID id, UUID appointmentId, UUID patientId, UUID practitionerId, LocalDateTime startsAt,
        Channel channel, ReminderOutcome outcome, NotSentReason reason, String address, LocalDateTime recordedAt) {

    public Reminder {
        if (id == null || appointmentId == null || patientId == null || practitionerId == null || startsAt == null
                || channel == null || outcome == null || recordedAt == null) {
            throw new IllegalArgumentException("A reminder needs its appointment, channel, outcome and time");
        }
        if ((outcome == ReminderOutcome.NOT_SENT) != (reason != null)) {
            throw new IllegalArgumentException("A reminder has a reason exactly when it was not sent");
        }
    }

    /** It went out, by email. */
    public static Reminder sent(BookedAppointment appointment, String address, LocalDateTime at) {
        return new Reminder(UUID.randomUUID(), appointment.id(), appointment.patientId(), appointment.practitionerId(),
                appointment.startsAt(), Channel.EMAIL, ReminderOutcome.SENT, null, address, at);
    }

    /** It was tried once, by email, and did not go out: reception is to call the patient instead. */
    public static Reminder failed(BookedAppointment appointment, String address, LocalDateTime at) {
        return new Reminder(UUID.randomUUID(), appointment.id(), appointment.patientId(), appointment.practitionerId(),
                appointment.startsAt(), Channel.EMAIL, ReminderOutcome.FAILED, null, address, at);
    }

    /** It was not sent, for that reason. */
    public static Reminder notSent(BookedAppointment appointment, NotSentReason reason, LocalDateTime at) {
        return new Reminder(UUID.randomUUID(), appointment.id(), appointment.patientId(), appointment.practitionerId(),
                appointment.startsAt(), Channel.EMAIL, ReminderOutcome.NOT_SENT, reason, null, at);
    }

    /**
     * Whether it settles the appointment at that time: sent or tried, it is not tried again. One not sent
     * does not: a consent recorded the next day may let it go out then.
     */
    public boolean settles(BookedAppointment appointment) {
        return outcome != ReminderOutcome.NOT_SENT && appointmentId.equals(appointment.id())
                && startsAt.equals(appointment.startsAt());
    }
}
