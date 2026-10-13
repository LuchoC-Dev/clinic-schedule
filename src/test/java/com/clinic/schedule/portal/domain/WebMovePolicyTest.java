package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.WebMoveRefusedException;
import com.clinic.schedule.portal.domain.exception.WebMoveRefusedException.Reason;

/**
 * Where a patient moves an appointment on the web (D-93): a booked one, up to a day before it, to a time the agenda
 * offers from tomorrow to 30 days on, and not the one it has.
 */
class WebMovePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final LocalDateTime BOOKED_AT = LocalDateTime.of(2026, 3, 13, 10, 0);
    private static final List<LocalTime> OFFERED = List.of(LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalTime.of(11, 30));

    private static Appointment at(LocalDateTime startsAt, Appointment.State state) {
        return new Appointment(UUID.randomUUID(), UUID.randomUUID(), startsAt, "consulta", state, null);
    }

    private static Appointment booked() {
        return at(BOOKED_AT, Appointment.State.BOOKED);
    }

    private static Reason refusal(Appointment appointment, LocalDateTime startsAt) {
        try {
            WebMovePolicy.check(appointment, startsAt, OFFERED, NOW, 30, 24);
            return null;
        } catch (WebMoveRefusedException e) {
            return e.reason();
        }
    }

    @Test
    void toAnOfferedTimeOfAnotherDayOrOfTheSameOne() {
        assertThatNoException().isThrownBy(() -> WebMovePolicy.check(booked(), LocalDateTime.of(2026, 3, 20, 11, 30),
                OFFERED, NOW, 30, 24));
        assertThat(refusal(booked(), BOOKED_AT.withHour(9))).isNull();
    }

    @Test
    void onlyABookedAppointmentUpToADayBeforeIt() {
        assertThat(refusal(at(BOOKED_AT, Appointment.State.CANCELLED), BOOKED_AT.withHour(9)))
                .isEqualTo(Reason.NOT_BOOKED);
        assertThat(refusal(at(BOOKED_AT, Appointment.State.ABSENT), BOOKED_AT.withHour(9)))
                .isEqualTo(Reason.NOT_BOOKED);
        assertThat(refusal(at(NOW.plusHours(24), Appointment.State.BOOKED), BOOKED_AT.withHour(9))).isNull();
        assertThat(refusal(at(NOW.plusHours(24).minusMinutes(1), Appointment.State.BOOKED), BOOKED_AT.withHour(9)))
                .isEqualTo(Reason.TOO_LATE);
    }

    @Test
    void toADayFromTomorrowTo30DaysOn() {
        assertThat(refusal(booked(), NOW.toLocalDate().atTime(LocalTime.of(10, 0)))).isEqualTo(Reason.DAY_OUT_OF_RANGE);
        assertThat(refusal(booked(), NOW.toLocalDate().plusDays(1).atTime(LocalTime.of(10, 0)))).isNull();
        assertThat(refusal(booked(), NOW.toLocalDate().plusDays(30).atTime(LocalTime.of(10, 0)))).isNull();
        assertThat(refusal(booked(), NOW.toLocalDate().plusDays(31).atTime(LocalTime.of(10, 0))))
                .isEqualTo(Reason.DAY_OUT_OF_RANGE);
    }

    @Test
    void toATimeTheAgendaOffersAndNotTheOneItHas() {
        assertThat(refusal(booked(), BOOKED_AT.withHour(9).withMinute(45))).isEqualTo(Reason.NOT_AN_OFFERED_TIME);
        assertThat(refusal(booked(), BOOKED_AT)).isEqualTo(Reason.SAME_TIME);
        assertThat(refusal(booked(), BOOKED_AT.plusDays(1))).isNull();
    }

    @Test
    void itMayBeMovedWhenItMayBeCancelled() {
        assertThat(WebMovePolicy.canMove(booked(), NOW, 24)).isTrue();
        assertThat(WebMovePolicy.canMove(at(BOOKED_AT, Appointment.State.CANCELLED), NOW, 24)).isFalse();
        assertThat(WebMovePolicy.canMove(at(NOW.plusHours(2), Appointment.State.BOOKED), NOW, 24)).isFalse();
    }

    @Test
    void theTimesOfADayAreAskedForOnlyWhileItMayBeMoved() {
        assertThatThrownBy(() -> WebMovePolicy.requireMovable(at(NOW.plusHours(2), Appointment.State.BOOKED), NOW, 24))
                .isInstanceOfSatisfying(WebMoveRefusedException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.TOO_LATE));
        assertThatThrownBy(() -> WebMovePolicy.requireDay(NOW.toLocalDate(), NOW.toLocalDate(), 30))
                .isInstanceOfSatisfying(WebMoveRefusedException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.DAY_OUT_OF_RANGE));
    }
}
