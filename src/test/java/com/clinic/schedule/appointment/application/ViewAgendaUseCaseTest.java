package com.clinic.schedule.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * The agenda over a span of days, for one practitioner or everyone, and who may read what
 * (D-04, D-35).
 */
class ViewAgendaUseCaseTest {

    /** A Tuesday. */
    private static final LocalDate TUESDAY = LocalDate.of(2026, 3, 10);

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final PractitionerId arias = PractitionerId.newId();
    private final PractitionerId benitez = PractitionerId.newId();

    private Appointment stored(PractitionerId practitioner, LocalDateTime startsAt, AppointmentStatus status) {
        return Appointment.restore(AppointmentId.newId(), PatientId.newId(), practitioner,
                TimeSlot.of(startsAt, 30), "consulta", null, status, null, false);
    }

    private Appointment booked(PractitionerId practitioner, LocalDateTime startsAt) {
        return stored(practitioner, startsAt, AppointmentStatus.BOOKED);
    }

    private ViewAgendaUseCase asReception() {
        return new ViewAgendaUseCase(appointments, Optional::empty);
    }

    private ViewAgendaUseCase as(PractitionerId practitioner) {
        return new ViewAgendaUseCase(appointments, () -> Optional.of(practitioner));
    }

    @Test
    void aDayOfOnePractitionerComesInOrderWithoutCancelledOnes() {
        Appointment late = booked(arias, TUESDAY.atTime(15, 0));
        Appointment early = booked(arias, TUESDAY.atTime(9, 0));
        appointments.given(late, early, stored(arias, TUESDAY.atTime(11, 0), AppointmentStatus.CANCELLED),
                booked(benitez, TUESDAY.atTime(10, 0)), booked(arias, TUESDAY.plusDays(1).atTime(9, 0)));

        var agenda = asReception().handle(Optional.of(arias), TUESDAY, TUESDAY);

        assertThat(agenda.practitionerId()).contains(arias);
        assertThat(agenda.appointments()).extracting(Appointment::id).containsExactly(early.id(), late.id());
    }

    @Test
    void withoutAPractitionerReceptionReadsEveryone() {
        Appointment ariasAtTen = booked(arias, TUESDAY.atTime(10, 0));
        Appointment benitezAtNine = booked(benitez, TUESDAY.atTime(9, 0));
        appointments.given(ariasAtTen, benitezAtNine);

        var agenda = asReception().handle(Optional.empty(), TUESDAY, TUESDAY);

        assertThat(agenda.practitionerId()).isEmpty();
        assertThat(agenda.appointments()).extracting(Appointment::id)
                .containsExactly(benitezAtNine.id(), ariasAtTen.id());
    }

    @Test
    void aSpanIncludesBothOfItsEnds() {
        LocalDate monday = TUESDAY.minusDays(1);
        LocalDate sunday = monday.plusDays(6);
        Appointment first = booked(arias, monday.atStartOfDay());
        Appointment last = booked(arias, sunday.atTime(23, 30));
        appointments.given(first, last, booked(arias, monday.minusDays(1).atTime(23, 30)),
                booked(arias, sunday.plusDays(1).atStartOfDay()));

        var agenda = asReception().handle(Optional.of(arias), monday, sunday);

        assertThat(agenda.appointments()).extracting(Appointment::id).containsExactly(first.id(), last.id());
    }

    @Test
    void aPractitionerAskingForNoOneGetsTheirOwn() {
        Appointment own = booked(arias, TUESDAY.atTime(10, 0));
        appointments.given(own, booked(benitez, TUESDAY.atTime(9, 0)));

        var agenda = as(arias).handle(Optional.empty(), TUESDAY, TUESDAY.plusDays(6));

        assertThat(agenda.practitionerId()).contains(arias);
        assertThat(agenda.appointments()).extracting(Appointment::id).containsExactly(own.id());
    }

    @Test
    void aPractitionerAskingForThemselvesGetsTheirOwn() {
        Appointment own = booked(arias, TUESDAY.atTime(10, 0));
        appointments.given(own);

        assertThat(as(arias).handle(Optional.of(arias), TUESDAY, TUESDAY).appointments())
                .extracting(Appointment::id).containsExactly(own.id());
    }

    /** Refused, not answered with their own: they would be reading a different agenda than they asked for (D-39). */
    @Test
    void aPractitionerAskingForSomeoneElseIsRefused() {
        appointments.given(booked(benitez, TUESDAY.atTime(9, 0)));

        assertThatThrownBy(() -> as(arias).handle(Optional.of(benitez), TUESDAY, TUESDAY))
                .isInstanceOf(AgendaAccessDeniedException.class);
    }

    @Test
    void aSpanThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> asReception().handle(Optional.empty(), TUESDAY, TUESDAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
