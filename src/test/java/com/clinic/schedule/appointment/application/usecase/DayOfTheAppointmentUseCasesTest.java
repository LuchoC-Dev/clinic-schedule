package com.clinic.schedule.appointment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentDayService;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.BookingChannel;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;
import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.Cancellations;

/**
 * Check-in, the late-arrival notice and the sweep of overdue appointments, with the clock
 * fixed so the deadlines can be tested to the minute (D-10).
 */
class DayOfTheAppointmentUseCasesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();

    private static Clock at(LocalDateTime now) {
        return Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
    }

    private Appointment stored(LocalDateTime startsAt) {
        return Appointment.restore(AppointmentId.newId(), PatientId.newId(), PractitionerId.newId(),
                TimeSlot.of(startsAt, 30), "consulta", null, AppointmentStatus.BOOKED, false, null,
                        BookingChannel.RECEPTION, false);
    }

    private final FakeArrivals arrivals = new FakeArrivals();
    private static final AppointmentDayService DAY = new AppointmentDayService();

    private MarkAbsentAppointmentsUseCase sweepAt(LocalDateTime now) {
        return new MarkAbsentAppointmentsUseCase(appointments, arrivals, DAY, at(now));
    }

    @Test
    void aLateArrivalNoticeIsRefusedOnceThePatientArrived() {
        Appointment appointment = stored(TEN);
        appointments.given(appointment);
        arrivals.arrived(appointment.id(), TEN.minusMinutes(5));

        assertThatThrownBy(() -> new RecordLateArrivalNoticeUseCase(appointments, arrivals, DAY, at(TEN))
                .handle(appointment.id())).isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void aLateArrivalNoticeOnAnUnknownAppointmentFails() {
        assertThatThrownBy(() -> new RecordLateArrivalNoticeUseCase(appointments, arrivals, DAY, at(TEN))
                .handle(AppointmentId.newId())).isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void aLateArrivalNoticeIsStored() {
        Appointment appointment = stored(TEN);
        appointments.given(appointment);

        new RecordLateArrivalNoticeUseCase(appointments, arrivals, DAY, at(TEN.minusHours(1))).handle(appointment.id());

        assertThat(appointments.findById(appointment.id()).orElseThrow().hasLateArrivalNotice()).isTrue();
    }

    @Test
    void theSweepMarksAbsentOnlyWhatIsDue() {
        Appointment overdue = stored(TEN.minusMinutes(30));
        Appointment notYet = stored(TEN.minusMinutes(29));
        Appointment later = stored(TEN.plusHours(2));
        appointments.given(overdue, notYet, later);

        var absent = sweepAt(TEN).handle();

        assertThat(absent).extracting(Appointment::id).containsExactly(overdue.id());
        assertThat(appointments.findById(overdue.id()).orElseThrow().status()).isEqualTo(AppointmentStatus.ABSENT);
        assertThat(appointments.findById(notYet.id()).orElseThrow().isBooked()).isTrue();
        assertThat(appointments.findById(later.id()).orElseThrow().isBooked()).isTrue();
    }

    @Test
    void theSweepLeavesALateArrivalAloneUntilItsDayIsOver() {
        Appointment warned = DAY.lateArrivalNotice(stored(TEN), TEN, Optional.empty());
        appointments.given(warned);
        LocalDateTime midnight = TEN.toLocalDate().plusDays(1).atStartOfDay();

        assertThat(sweepAt(midnight.minusMinutes(1)).handle()).isEmpty();
        assertThat(sweepAt(midnight).handle()).hasSize(1);
    }

    @Test
    void theSweepNeverMarksAbsentAPatientWhoArrived() {
        Appointment arrived = stored(TEN);
        appointments.given(arrived);
        arrivals.arrived(arrived.id(), TEN.plusMinutes(5));

        assertThat(sweepAt(TEN.plusDays(2)).handle()).isEmpty();
        assertThat(appointments.findById(arrived.id()).orElseThrow().isBooked()).isTrue();
    }

    @Test
    void theSweepIgnoresWhatWasAlreadyCancelled() {
        Appointment cancelled = stored(TEN).cancelled(Cancellations.byHand());
        appointments.given(cancelled);

        assertThat(sweepAt(TEN.plusHours(1)).handle()).isEmpty();
        assertThat(appointments.saveCount()).isZero();
    }
}
