package com.clinic.schedule.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

class RescheduleAndCancelUseCaseTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final PatientId ANA = new PatientId(1L);
    private static final PatientId LUIS = new PatientId(2L);
    private static final PractitionerId ARIAS = new PractitionerId(7L);

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final RescheduleAppointmentUseCase reschedule =
            new RescheduleAppointmentUseCase(appointments, new BookingService());
    private final CancelAppointmentUseCase cancel = new CancelAppointmentUseCase(appointments);

    private Appointment stored(PatientId patient, int atMinute, int minutes) {
        return Appointment.restore(AppointmentId.newId(), patient, ARIAS,
                TimeSlot.of(TEN.plusMinutes(atMinute), minutes), "consulta", null, AppointmentStatus.BOOKED);
    }

    @Test
    void movesTheAppointmentAndStoresTheNewSlot() {
        Appointment original = stored(ANA, 0, 30);
        appointments.given(original);

        Appointment moved = reschedule.handle(
                new RescheduleAppointmentUseCase.Command(original.id(), TEN.plusHours(3), 45));

        assertThat(moved.slot()).isEqualTo(TimeSlot.of(TEN.plusHours(3), 45));
        assertThat(appointments.findById(original.id())).contains(moved);
        assertThat(appointments.storedCount()).isEqualTo(1);
    }

    @Test
    void aSmallMoveDoesNotCollideWithTheSlotBeingLeftBehind() {
        Appointment original = stored(ANA, 0, 30);
        appointments.given(original);

        Appointment moved = reschedule.handle(
                new RescheduleAppointmentUseCase.Command(original.id(), TEN.plusMinutes(10), 30));

        assertThat(moved.slot().startsAt()).isEqualTo(TEN.plusMinutes(10));
    }

    @Test
    void aRejectedMoveStoresNothing() {
        Appointment original = stored(ANA, 0, 30);
        Appointment other = stored(LUIS, 120, 30);
        appointments.given(original, other);

        assertThatThrownBy(() -> reschedule.handle(
                new RescheduleAppointmentUseCase.Command(original.id(), TEN.plusMinutes(120), 30)))
                .isInstanceOf(PractitionerAlreadyBookedException.class);

        assertThat(appointments.saveCount()).isZero();
        assertThat(appointments.findById(original.id())).contains(original);
    }

    @Test
    void reschedulingAnUnknownAppointmentFails() {
        assertThatThrownBy(() -> reschedule.handle(
                new RescheduleAppointmentUseCase.Command(AppointmentId.newId(), TEN, 30)))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void cancellingKeepsTheAppointmentAsACancelledRecord() {
        Appointment original = stored(ANA, 0, 30);
        appointments.given(original);

        Appointment cancelled = cancel.handle(original.id());

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointments.findById(original.id())).contains(cancelled);
        assertThat(appointments.storedCount()).isEqualTo(1);
    }

    @Test
    void cancellingTwiceFails() {
        Appointment original = stored(ANA, 0, 30);
        appointments.given(original);
        cancel.handle(original.id());

        assertThatThrownBy(() -> cancel.handle(original.id()))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void aCancelledAppointmentCannotBeRescheduled() {
        Appointment original = stored(ANA, 0, 30);
        appointments.given(original);
        cancel.handle(original.id());

        assertThatThrownBy(() -> reschedule.handle(
                new RescheduleAppointmentUseCase.Command(original.id(), TEN.plusHours(3), 30)))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void cancellingAnUnknownAppointmentFails() {
        assertThatThrownBy(() -> cancel.handle(AppointmentId.newId()))
                .isInstanceOf(AppointmentNotFoundException.class);
    }
}
