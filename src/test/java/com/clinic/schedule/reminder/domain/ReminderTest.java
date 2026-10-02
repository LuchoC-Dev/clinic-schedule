package com.clinic.schedule.reminder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** What is recorded of a reminder, and when it settles an appointment (D-78). */
class ReminderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final BookedAppointment APPOINTMENT = new BookedAppointment(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), LocalDateTime.of(2026, 3, 12, 10, 0));

    @Test
    void aReminderHasAReasonExactlyWhenItWasNotSent() {
        assertThat(Reminder.notSent(APPOINTMENT, NotSentReason.NO_CONSENT, NOW).reason())
                .isEqualTo(NotSentReason.NO_CONSENT);
        assertThat(Reminder.sent(APPOINTMENT, "a@mail.com", NOW).reason()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new Reminder(UUID.randomUUID(), APPOINTMENT.id(),
                APPOINTMENT.patientId(), APPOINTMENT.practitionerId(), APPOINTMENT.startsAt(), Channel.EMAIL,
                ReminderOutcome.SENT, NotSentReason.NO_EMAIL, "a@mail.com", NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new Reminder(UUID.randomUUID(), APPOINTMENT.id(),
                APPOINTMENT.patientId(), APPOINTMENT.practitionerId(), APPOINTMENT.startsAt(), Channel.EMAIL,
                ReminderOutcome.NOT_SENT, null, null, NOW));
    }

    @Test
    void sentOrFailedItSettlesTheAppointmentAtThatTimeOnly() {
        BookedAppointment moved = new BookedAppointment(APPOINTMENT.id(), APPOINTMENT.patientId(),
                APPOINTMENT.practitionerId(), APPOINTMENT.startsAt().plusHours(2));
        BookedAppointment another = new BookedAppointment(UUID.randomUUID(), APPOINTMENT.patientId(),
                APPOINTMENT.practitionerId(), APPOINTMENT.startsAt());

        assertThat(Reminder.sent(APPOINTMENT, "a@mail.com", NOW).settles(APPOINTMENT)).isTrue();
        assertThat(Reminder.failed(APPOINTMENT, "a@mail.com", NOW).settles(APPOINTMENT)).isTrue();
        assertThat(Reminder.sent(APPOINTMENT, "a@mail.com", NOW).settles(moved)).isFalse();
        assertThat(Reminder.sent(APPOINTMENT, "a@mail.com", NOW).settles(another)).isFalse();
        assertThat(Reminder.notSent(APPOINTMENT, NotSentReason.NO_CONSENT, NOW).settles(APPOINTMENT)).isFalse();
    }
}
