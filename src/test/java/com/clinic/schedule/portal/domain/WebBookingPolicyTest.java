package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException.Reason;

/**
 * What a patient may book on the web (D-82): a reason, a day, an offered time, and three ahead at most — one for a
 * patient who registered on the web, until reception verifies them (D-91).
 */
class WebBookingPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final BookableReason ON_THE_WEB = reason(true);
    private static final List<LocalTime> OFFERED = List.of(LocalTime.of(10, 0), LocalTime.of(10, 30));

    private static BookableReason reason(boolean webBookable) {
        return new BookableReason(UUID.randomUUID(), "Dra. Arias", UUID.randomUUID(), "consulta", 30, webBookable);
    }

    private static Appointment appointment(LocalDateTime startsAt, Appointment.State state) {
        return new Appointment(UUID.randomUUID(), UUID.randomUUID(), startsAt, "consulta", state, null);
    }

    private static Reason refusal(BookableReason reason, LocalDateTime startsAt, List<Appointment> appointments) {
        return refusal(reason, startsAt, appointments, false);
    }

    private static Reason refusal(BookableReason reason, LocalDateTime startsAt, List<Appointment> appointments,
            boolean awaitsVerification) {
        try {
            WebBookingPolicy.check(reason, startsAt, OFFERED, appointments, awaitsVerification, NOW, 30);
        } catch (WebBookingRefusedException e) {
            return e.reason();
        }
        return null;
    }

    @Test
    void onlyAReasonReceptionOpensToTheWeb() {
        LocalDateTime tomorrow = LocalDate.of(2026, 3, 11).atTime(10, 0);

        assertThat(refusal(ON_THE_WEB, tomorrow, List.of())).isNull();
        assertThat(refusal(reason(false), tomorrow, List.of())).isEqualTo(Reason.NOT_ON_THE_WEB);
        assertThat(refusal(null, tomorrow, List.of())).isEqualTo(Reason.NOT_ON_THE_WEB);
    }

    @Test
    void fromTomorrowToThirtyDaysAhead() {
        // Later today, at a time the agenda would offer, is still reception's.
        assertThat(refusal(ON_THE_WEB, LocalDate.of(2026, 3, 10).atTime(18, 0), List.of()))
                .isEqualTo(Reason.DAY_OUT_OF_RANGE);
        assertThat(refusal(ON_THE_WEB, LocalDate.of(2026, 3, 11).atTime(10, 0), List.of())).isNull();
        assertThat(refusal(ON_THE_WEB, LocalDate.of(2026, 4, 9).atTime(10, 0), List.of())).isNull();
        assertThat(refusal(ON_THE_WEB, LocalDate.of(2026, 4, 10).atTime(10, 0), List.of()))
                .isEqualTo(Reason.DAY_OUT_OF_RANGE);
        assertThat(WebBookingPolicy.firstDay(NOW.toLocalDate())).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(WebBookingPolicy.lastDay(NOW.toLocalDate(), 30)).isEqualTo(LocalDate.of(2026, 4, 9));
    }

    @Test
    void onlyATimeTheAgendaOffers() {
        assertThat(refusal(ON_THE_WEB, LocalDate.of(2026, 3, 11).atTime(10, 15), List.of()))
                .isEqualTo(Reason.NOT_AN_OFFERED_TIME);
    }

    @Test
    void threeAppointmentsAheadAtMostWhoeverBookedThem() {
        LocalDateTime tomorrow = LocalDate.of(2026, 3, 11).atTime(10, 0);
        List<Appointment> appointments = new ArrayList<>(List.of(
                appointment(NOW, Appointment.State.BOOKED),
                appointment(NOW.plusDays(5), Appointment.State.BOOKED),
                appointment(NOW.plusDays(6), Appointment.State.CANCELLED),
                appointment(NOW.plusDays(7), Appointment.State.ABSENT),
                appointment(NOW.minusMinutes(1), Appointment.State.BOOKED)));
        assertThatNoException().isThrownBy(() -> WebBookingPolicy.check(ON_THE_WEB, tomorrow, OFFERED, appointments,
                false, NOW, 30));

        appointments.add(appointment(NOW.plusDays(8), Appointment.State.BOOKED));
        assertThat(refusal(ON_THE_WEB, tomorrow, appointments)).isEqualTo(Reason.TOO_MANY_AHEAD);
    }

    @Test
    void oneAppointmentAheadUntilReceptionVerifiesAPatientWhoRegisteredOnTheWeb() {
        LocalDateTime tomorrow = LocalDate.of(2026, 3, 11).atTime(10, 0);
        List<Appointment> appointments = new ArrayList<>(List.of(
                appointment(NOW.plusDays(6), Appointment.State.CANCELLED),
                appointment(NOW.minusMinutes(1), Appointment.State.BOOKED)));
        assertThat(refusal(ON_THE_WEB, tomorrow, appointments, true)).isNull();

        appointments.add(appointment(NOW.plusDays(5), Appointment.State.BOOKED));
        assertThat(refusal(ON_THE_WEB, tomorrow, appointments, true)).isEqualTo(Reason.ONE_AHEAD_UNTIL_VERIFIED);
        assertThat(refusal(ON_THE_WEB, tomorrow, appointments, false)).isNull();
    }

    @Test
    void onlyAPatientWhoRegisteredOnTheWebAndIsNotVerifiedAwaitsVerification() {
        UUID id = UUID.randomUUID();
        LocalDate born = LocalDate.of(1990, 5, 14);

        assertThat(new AccountHolder(id, "Ana Pérez", false, born, true).awaitsVerification()).isTrue();
        assertThat(new AccountHolder(id, "Ana Pérez", true, born, true).awaitsVerification()).isFalse();
        // Registered at reception and not verified, as a child of a family group may be (D-84): the usual three.
        assertThat(new AccountHolder(id, "Ana Pérez", false, born, false).awaitsVerification()).isFalse();
    }
}
