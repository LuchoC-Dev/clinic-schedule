package com.clinic.schedule.appointment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.application.exception.UnknownReasonException;
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

    /** The practice's clock, stopped at the start of the day these tests book on. */
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 3, 10, 0, 0).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant(),
            ZoneId.of("America/Argentina/Buenos_Aires"));

    private final SuggestSlotsUseCase suggest = new SuggestSlotsUseCase(appointments, practitioners, CLOCK);

    private final UUID consulta = practitioners.givenReason(ARIAS, "consulta", 30);

    {
        practitioners.worksOnly(ARIAS, DAY.atTime(9, 0), DAY.atTime(11, 0));
    }

    private static Appointment booked(LocalTime at, int minutes) {
        return Appointment.restore(AppointmentId.newId(), PatientId.newId(), ARIAS, TimeSlot.of(DAY.atTime(at), minutes),
                "consulta", null, AppointmentStatus.BOOKED, false);
    }

    private static List<LocalTime> times(String... values) {
        return java.util.Arrays.stream(values).map(LocalTime::parse).toList();
    }

    @Test
    void offersEveryQuarterOfAnHourInsideTheWorkingHours() {
        assertThat(suggest.forReason(ARIAS, consulta, DAY).times())
                .isEqualTo(times("09:00", "09:15", "09:30", "09:45", "10:00", "10:15", "10:30"));
    }

    @Test
    void theLastOfferEndsExactlyWhenTheHoursEnd() {
        UUID longOne = practitioners.givenReason(ARIAS, "ecografia", 60);

        assertThat(suggest.forReason(ARIAS, longOne, DAY).times()).last().isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void leavesOutWhatWouldOverlapABookedAppointment() {
        appointments.given(booked(LocalTime.of(9, 30), 30));

        assertThat(suggest.forReason(ARIAS, consulta, DAY).times())
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

        assertThat(suggest.forMoving(moving, DAY).times())
                .isEqualTo(times("09:00", "09:15", "09:30", "09:45", "10:00"));
    }

    @Test
    void aTimeAlreadyGoneIsNotOffered() {
        Clock atTwentyToTen = Clock.fixed(
                DAY.atTime(9, 40).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant(),
                ZoneId.of("America/Argentina/Buenos_Aires"));

        assertThat(new SuggestSlotsUseCase(appointments, practitioners, atTwentyToTen).forReason(ARIAS, consulta, DAY).times())
                .first().isEqualTo(LocalTime.of(9, 45));
    }

    // --- whether the practitioner works that day (D-45) ----------------------------------

    @Test
    void aDayWithTimesIsADayTheyWork() {
        assertThat(suggest.forReason(ARIAS, consulta, DAY).practitionerWorks()).isTrue();
    }

    @Test
    void aDayTheyDoNotWorkIsToldApartFromAFullOne() {
        var otherDay = suggest.forReason(ARIAS, consulta, DAY.plusDays(1));

        assertThat(otherDay.times()).isEmpty();
        assertThat(otherDay.practitionerWorks()).isFalse();
    }

    @Test
    void aFullDayIsStillADayTheyWork() {
        appointments.given(booked(LocalTime.of(9, 0), 120));

        var full = suggest.forReason(ARIAS, consulta, DAY);

        assertThat(full.times()).isEmpty();
        assertThat(full.practitionerWorks()).isTrue();
    }
}
