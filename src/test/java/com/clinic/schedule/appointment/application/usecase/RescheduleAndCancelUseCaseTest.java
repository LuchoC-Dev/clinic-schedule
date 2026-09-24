package com.clinic.schedule.appointment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.exception.OutsideWorkingHoursException;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;
import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.exception.PractitionerAlreadyBookedException;

class RescheduleAndCancelUseCaseTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final PatientId ANA = PatientId.newId();
    private static final PatientId LUIS = PatientId.newId();
    private static final PractitionerId ARIAS = PractitionerId.newId();

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final FakePractitionerSchedule practitioners = new FakePractitionerSchedule();

    /** The practice's clock, stopped at the start of the day these tests book on. */
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 3, 10, 0, 0).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant(),
            ZoneId.of("America/Argentina/Buenos_Aires"));

    private final RescheduleAppointmentUseCase reschedule =
            new RescheduleAppointmentUseCase(appointments, practitioners, new BookingService(), CLOCK);
    private final CancelAppointmentUseCase cancel = new CancelAppointmentUseCase(appointments);

    private Appointment stored(PatientId patient, int atMinute, int minutes) {
        return Appointment.restore(AppointmentId.newId(), patient, ARIAS,
                TimeSlot.of(TEN.plusMinutes(atMinute), minutes), "consulta", null, AppointmentStatus.BOOKED, null, false);
    }

    private RescheduleAppointmentUseCase.Command moveTo(Appointment appointment, LocalDateTime startsAt) {
        return new RescheduleAppointmentUseCase.Command(appointment.id(), startsAt);
    }

    @Test
    void movesTheAppointmentAndKeepsItsDuration() {
        Appointment original = stored(ANA, 0, 45);
        appointments.given(original);

        Appointment moved = reschedule.handle(moveTo(original, TEN.plusHours(3)));

        assertThat(moved.slot()).isEqualTo(TimeSlot.of(TEN.plusHours(3), 45));
        assertThat(appointments.findById(original.id())).contains(moved);
        assertThat(appointments.storedCount()).isEqualTo(1);
    }

    @Test
    void aSmallMoveDoesNotCollideWithTheSlotBeingLeftBehind() {
        Appointment original = stored(ANA, 0, 30);
        appointments.given(original);

        Appointment moved = reschedule.handle(moveTo(original, TEN.plusMinutes(10)));

        assertThat(moved.slot().startsAt()).isEqualTo(TEN.plusMinutes(10));
    }

    @Test
    void aMoveOutsideTheWorkingHoursIsRejectedAndStoresNothing() {
        Appointment original = stored(ANA, 0, 30);
        appointments.given(original);
        practitioners.worksOnly(ARIAS, TEN, TEN.plusHours(2));

        assertThatThrownBy(() -> reschedule.handle(moveTo(original, TEN.plusHours(5))))
                .isInstanceOf(OutsideWorkingHoursException.class);

        assertThat(appointments.saveCount()).isZero();
        assertThat(appointments.findById(original.id())).contains(original);
    }

    @Test
    void aMoveOntoAnotherAppointmentIsRejectedAndStoresNothing() {
        Appointment original = stored(ANA, 0, 30);
        Appointment other = stored(LUIS, 120, 30);
        appointments.given(original, other);

        assertThatThrownBy(() -> reschedule.handle(moveTo(original, TEN.plusMinutes(120))))
                .isInstanceOf(PractitionerAlreadyBookedException.class);

        assertThat(appointments.saveCount()).isZero();
        assertThat(appointments.findById(original.id())).contains(original);
    }

    @Test
    void reschedulingAnUnknownAppointmentFails() {
        assertThatThrownBy(() -> reschedule.handle(new RescheduleAppointmentUseCase.Command(AppointmentId.newId(), TEN)))
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

        assertThatThrownBy(() -> reschedule.handle(moveTo(original, TEN.plusHours(3))))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void cancellingAnUnknownAppointmentFails() {
        assertThatThrownBy(() -> cancel.handle(AppointmentId.newId()))
                .isInstanceOf(AppointmentNotFoundException.class);
    }
}
