package com.clinic.schedule.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

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
                TimeSlot.of(startsAt, 30), "consulta", null, AppointmentStatus.BOOKED, null, false);
    }

    private CancelOverdueAppointmentsUseCase sweepAt(LocalDateTime now) {
        return new CancelOverdueAppointmentsUseCase(appointments, at(now));
    }

    @Test
    void checkingInStoresTheTimeFromTheClock() {
        Appointment appointment = stored(TEN);
        appointments.given(appointment);

        new CheckInUseCase(appointments, at(TEN.minusMinutes(7))).handle(appointment.id());

        assertThat(appointments.findById(appointment.id()).orElseThrow().checkedInAt())
                .isEqualTo(TEN.minusMinutes(7));
    }

    @Test
    void checkingInAnUnknownAppointmentFails() {
        assertThatThrownBy(() -> new CheckInUseCase(appointments, at(TEN)).handle(AppointmentId.newId()))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void suspendingIsStored() {
        Appointment appointment = stored(TEN);
        appointments.given(appointment);

        new SuspendAutoCancellationUseCase(appointments, at(TEN.minusHours(1))).handle(appointment.id());

        assertThat(appointments.findById(appointment.id()).orElseThrow().isAutoCancellationSuspended()).isTrue();
    }

    @Test
    void theSweepCancelsOnlyWhatIsDue() {
        Appointment overdue = stored(TEN.minusMinutes(30));
        Appointment notYet = stored(TEN.minusMinutes(29));
        Appointment later = stored(TEN.plusHours(2));
        appointments.given(overdue, notYet, later);

        var cancelled = sweepAt(TEN).handle();

        assertThat(cancelled).extracting(Appointment::id).containsExactly(overdue.id());
        assertThat(appointments.findById(overdue.id()).orElseThrow().status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointments.findById(notYet.id()).orElseThrow().isBooked()).isTrue();
        assertThat(appointments.findById(later.id()).orElseThrow().isBooked()).isTrue();
    }

    @Test
    void theSweepLeavesALateArrivalAloneUntilTheNextDay() {
        Appointment warned = stored(TEN).withAutoCancellationSuspended(TEN);
        appointments.given(warned);

        assertThat(sweepAt(TEN.plusHours(3)).handle()).isEmpty();
        assertThat(sweepAt(TEN.plusHours(24)).handle()).hasSize(1);
    }

    @Test
    void theSweepNeverCancelsAPatientWhoArrived() {
        Appointment arrived = stored(TEN).checkedIn(TEN.plusMinutes(5));
        appointments.given(arrived);

        assertThat(sweepAt(TEN.plusDays(2)).handle()).isEmpty();
        assertThat(appointments.findById(arrived.id()).orElseThrow().isBooked()).isTrue();
    }

    @Test
    void theSweepIgnoresWhatWasAlreadyCancelled() {
        Appointment cancelled = stored(TEN).cancelled();
        appointments.given(cancelled);

        assertThat(sweepAt(TEN.plusHours(1)).handle()).isEmpty();
        assertThat(appointments.saveCount()).isZero();
    }
}
