package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException.Reason;

/** Until when a patient cancels on the web (D-83): a booked appointment, up to a day before it. */
class WebCancellationPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);

    private static Appointment at(LocalDateTime startsAt, Appointment.State state) {
        return new Appointment(UUID.randomUUID(), UUID.randomUUID(), startsAt, "consulta", state, null);
    }

    @Test
    void upToADayBeforeIt() {
        assertThat(WebCancellationPolicy.isInTime(NOW.plusHours(24), NOW, 24)).isTrue();
        assertThat(WebCancellationPolicy.isInTime(NOW.plusHours(24).minusMinutes(1), NOW, 24)).isFalse();
        assertThat(WebCancellationPolicy.isInTime(NOW.plusDays(3), NOW, 48)).isTrue();
        assertThat(WebCancellationPolicy.isInTime(NOW.plusHours(47), NOW, 48)).isFalse();
    }

    @Test
    void onlyABookedAppointment() {
        assertThat(WebCancellationPolicy.canCancel(at(NOW.plusDays(2), Appointment.State.BOOKED), NOW, 24)).isTrue();
        assertThat(WebCancellationPolicy.canCancel(at(NOW.plusDays(2), Appointment.State.CANCELLED), NOW, 24))
                .isFalse();
        assertThat(WebCancellationPolicy.canCancel(at(NOW.plusHours(2), Appointment.State.BOOKED), NOW, 24)).isFalse();
    }

    @Test
    void whyNotIsSaid() {
        assertThatNoException().isThrownBy(() -> WebCancellationPolicy.check(at(NOW.plusDays(2),
                Appointment.State.BOOKED), NOW, 24));
        assertThatThrownBy(() -> WebCancellationPolicy.check(at(NOW.plusDays(2), Appointment.State.ABSENT), NOW, 24))
                .isInstanceOfSatisfying(WebCancellationRefusedException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.NOT_BOOKED));
        assertThatThrownBy(() -> WebCancellationPolicy.check(at(NOW.plusHours(2), Appointment.State.BOOKED), NOW, 24))
                .isInstanceOfSatisfying(WebCancellationRefusedException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.TOO_LATE));
    }
}
