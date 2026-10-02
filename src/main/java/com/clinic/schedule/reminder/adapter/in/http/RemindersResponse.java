package com.clinic.schedule.reminder.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.reminder.application.usecase.ListRemindersUseCase;
import com.clinic.schedule.reminder.domain.Channel;
import com.clinic.schedule.reminder.domain.NotSentReason;
import com.clinic.schedule.reminder.domain.Reminder;
import com.clinic.schedule.reminder.domain.ReminderOutcome;

/**
 * The reminders as reception gets them (D-78): the appointment — its time, practitioner and patient —,
 * the channel, what happened and when. Never the address it went to, nor the text.
 */
record RemindersResponse(LocalDate from, LocalDate to, List<Entry> reminders) {

    /** {@code reason} is null unless it was not sent. */
    record Entry(UUID appointmentId, LocalDateTime startsAt, Practitioner practitioner, Patient patient,
            Channel channel, ReminderOutcome outcome, NotSentReason reason, LocalDateTime recordedAt) {
    }

    record Practitioner(UUID id, String fullName) {
    }

    /** {@code document} is null when the patient gave none. */
    record Patient(UUID id, String fullName, String document) {
    }

    static RemindersResponse of(ListRemindersUseCase.Result result, Map<UUID, PatientSummary> patients,
            Map<UUID, String> practitionerNames) {
        return new RemindersResponse(result.from(), result.to(), result.reminders().stream()
                .map(reminder -> entry(reminder, patients, practitionerNames))
                .toList());
    }

    private static Entry entry(Reminder reminder, Map<UUID, PatientSummary> patients,
            Map<UUID, String> practitionerNames) {
        PatientSummary patient = patients.get(reminder.patientId());
        return new Entry(reminder.appointmentId(), reminder.startsAt(),
                new Practitioner(reminder.practitionerId(), practitionerNames.get(reminder.practitionerId())),
                new Patient(reminder.patientId(), patient == null ? null : patient.fullName(),
                        patient == null ? null : patient.document()),
                reminder.channel(), reminder.outcome(), reminder.reason(), reminder.recordedAt());
    }
}
