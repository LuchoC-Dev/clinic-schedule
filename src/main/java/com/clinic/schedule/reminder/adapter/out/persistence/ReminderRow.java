package com.clinic.schedule.reminder.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.reminder.domain.Channel;
import com.clinic.schedule.reminder.domain.NotSentReason;
import com.clinic.schedule.reminder.domain.Reminder;
import com.clinic.schedule.reminder.domain.ReminderOutcome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A reminder as a row (D-78). Only ever inserted. The address is not a column: only its hash, which
 * answers whether a reminder went to a given address and cannot be turned back into it.
 */
@Entity
@Table(name = "reminder")
class ReminderRow {

    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReminderOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NotSentReason reason;

    @Column(name = "address_hash", length = 60)
    private String addressHash;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected ReminderRow() {
        // required by JPA
    }

    static ReminderRow from(Reminder reminder, String addressHash) {
        ReminderRow row = new ReminderRow();
        row.id = reminder.id();
        row.appointmentId = reminder.appointmentId();
        row.patientId = reminder.patientId();
        row.practitionerId = reminder.practitionerId();
        row.startsAt = reminder.startsAt();
        row.channel = reminder.channel();
        row.outcome = reminder.outcome();
        row.reason = reminder.reason();
        row.addressHash = addressHash;
        row.recordedAt = reminder.recordedAt();
        return row;
    }

    /** Without the address, which is not kept. */
    Reminder toReminder() {
        return new Reminder(id, appointmentId, patientId, practitionerId, startsAt, channel, outcome, reason, null,
                recordedAt);
    }
}
