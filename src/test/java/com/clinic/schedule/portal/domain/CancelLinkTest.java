package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The link to cancel an appointment from an email (D-83): its hash alone kept, and good for that time only. */
class CancelLinkTest {

    private static final UUID APPOINTMENT = UUID.randomUUID();
    private static final LocalDateTime THURSDAY = LocalDateTime.of(2026, 3, 12, 10, 0);

    @Test
    void onlyTheHashOfTheTokenIsKept() {
        CancelLink.Issued issued = CancelLink.issue(APPOINTMENT, THURSDAY);

        assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.stored().hash()).isEqualTo(SecretToken.hashOf(issued.token())).matches("[0-9a-f]{64}");
        assertThat(issued.stored().appointmentId()).isEqualTo(APPOINTMENT);
        assertThat(issued.stored().startsAt()).isEqualTo(THURSDAY);
    }

    @Test
    void itIsForItsAppointmentAtTheTimeItWasSentFor() {
        CancelLink link = CancelLink.issue(APPOINTMENT, THURSDAY).stored();
        UUID patient = UUID.randomUUID();
        UUID practitioner = UUID.randomUUID();

        assertThat(link.isFor(new BookedAppointment(APPOINTMENT, patient, practitioner, THURSDAY))).isTrue();
        assertThat(link.isFor(new BookedAppointment(APPOINTMENT, patient, practitioner, THURSDAY.plusHours(1))))
                .isFalse();
        assertThat(link.isFor(new BookedAppointment(UUID.randomUUID(), patient, practitioner, THURSDAY))).isFalse();
    }
}
