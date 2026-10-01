package com.clinic.schedule.appointment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/** A patient's appointments: all of them, cancelled ones included, the most recent first. */
class ViewPatientAppointmentsUseCaseTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final PatientId ana = PatientId.newId();

    private Appointment stored(PatientId patient, LocalDateTime startsAt, AppointmentStatus status) {
        return Appointment.restore(AppointmentId.newId(), patient, PractitionerId.newId(),
                TimeSlot.of(startsAt, 30), "consulta", null, status, false, null);
    }

    @Test
    void everyAppointmentOfThePatientComesBackTheMostRecentFirst() {
        Appointment lastMonth = stored(ana, TEN.minusMonths(1), AppointmentStatus.BOOKED);
        Appointment cancelled = stored(ana, TEN, AppointmentStatus.CANCELLED);
        Appointment nextWeek = stored(ana, TEN.plusWeeks(1), AppointmentStatus.BOOKED);
        appointments.given(lastMonth, nextWeek, cancelled, stored(PatientId.newId(), TEN, AppointmentStatus.BOOKED));

        var found = new ViewPatientAppointmentsUseCase(appointments).handle(ana);

        assertThat(found).extracting(Appointment::id)
                .containsExactly(nextWeek.id(), cancelled.id(), lastMonth.id());
    }

    @Test
    void aPatientWithoutAppointmentsHasAnEmptyList() {
        assertThat(new ViewPatientAppointmentsUseCase(appointments).handle(ana)).isEmpty();
    }
}
