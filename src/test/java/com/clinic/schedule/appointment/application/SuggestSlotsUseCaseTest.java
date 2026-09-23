package com.clinic.schedule.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

class SuggestSlotsUseCaseTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final PractitionerId ARIAS = PractitionerId.newId();

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final FakePractitionerSchedule practitioners = new FakePractitionerSchedule();
    private final SuggestSlotsUseCase suggest = new SuggestSlotsUseCase(appointments, practitioners);

    private final UUID consulta = practitioners.givenReason(ARIAS, "consulta", 30);

    {
        practitioners.worksOnly(ARIAS, DAY.atTime(9, 0), DAY.atTime(11, 0));
    }

    private static Appointment booked(LocalTime at, int minutes) {
        return Appointment.restore(AppointmentId.newId(), PatientId.newId(), ARIAS, TimeSlot.of(DAY.atTime(at), minutes),
                "consulta", null, AppointmentStatus.BOOKED, null, false);
    }

    private static List<LocalTime> times(String... values) {
        return java.util.Arrays.stream(values).map(LocalTime::parse).toList();
    }

    @Test
    void offersEveryQuarterOfAnHourInsideTheWorkingHours() {
        assertThat(suggest.forReason(ARIAS, consulta, DAY))
                .isEqualTo(times("09:00", "09:15", "09:30", "09:45", "10:00", "10:15", "10:30"));
    }

    @Test
    void theLastOfferEndsExactlyWhenTheHoursEnd() {
        UUID longOne = practitioners.givenReason(ARIAS, "ecografia", 60);

        assertThat(suggest.forReason(ARIAS, longOne, DAY)).last().isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void leavesOutWhatWouldOverlapABookedAppointment() {
        appointments.given(booked(LocalTime.of(9, 30), 30));

        assertThat(suggest.forReason(ARIAS, consulta, DAY))
                .isEqualTo(times("09:00", "10:00", "10:15", "10:30"));
    }

    @Test
    void anUnknownReasonIsRefused() {
        assertThatThrownBy(() -> suggest.forReason(ARIAS, UUID.randomUUID(), DAY))
                .isInstanceOf(UnknownReasonException.class);
    }

    @Test
    void whenMovingAnAppointmentItsOwnSlotDoesNotCountAsTaken() {
        Appointment moving = booked(LocalTime.of(9, 30), 30);
        appointments.given(moving, booked(LocalTime.of(10, 30), 30));

        assertThat(suggest.forMoving(moving, DAY))
                .isEqualTo(times("09:00", "09:15", "09:30", "09:45", "10:00"));
    }
}
