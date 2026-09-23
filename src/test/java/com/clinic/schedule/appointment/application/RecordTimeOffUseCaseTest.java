package com.clinic.schedule.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/** What an absence does to the agenda, with both ports answered from memory. */
class RecordTimeOffUseCaseTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final PractitionerId ARIAS = PractitionerId.newId();
    private static final PractitionerId BENITEZ = PractitionerId.newId();

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final FakePractitionerSchedule practitioners = new FakePractitionerSchedule();

    /** The practice's clock, stopped at the start of the day these tests book on. */
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 3, 10, 0, 0).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant(),
            ZoneId.of("America/Argentina/Buenos_Aires"));

    private final RecordTimeOffUseCase recordTimeOff = new RecordTimeOffUseCase(appointments, practitioners, CLOCK);

    private static Appointment booked(PractitionerId practitioner, LocalDateTime startsAt) {
        return Appointment.restore(AppointmentId.newId(), PatientId.newId(), practitioner,
                TimeSlot.of(startsAt, 30), "consulta", null, AppointmentStatus.BOOKED, null, false);
    }

    private static TimeSlot wholeDay() {
        return new TimeSlot(DAY.atStartOfDay(), Duration.ofDays(1));
    }

    private static TimeSlot between(int fromHour, int untilHour) {
        return new TimeSlot(DAY.atTime(fromHour, 0), Duration.ofHours(untilHour - fromHour));
    }

    private AppointmentStatus statusOf(Appointment appointment) {
        return appointments.findById(appointment.id()).orElseThrow().status();
    }

    @Test
    void aWholeDayOffCancelsEveryAppointmentOfThatDay() {
        Appointment morning = booked(ARIAS, DAY.atTime(9, 0));
        Appointment afternoon = booked(ARIAS, DAY.atTime(16, 0));
        appointments.given(morning, afternoon);

        RecordTimeOffUseCase.Result result = recordTimeOff.handle(new RecordTimeOffUseCase.Command(ARIAS, wholeDay()));

        assertThat(result.cancelled()).extracting(Appointment::id).containsExactlyInAnyOrder(morning.id(), afternoon.id());
        assertThat(result.needsManualHandling()).isEmpty();
        assertThat(statusOf(morning)).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(statusOf(afternoon)).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(practitioners.recordedTimeOff()).containsExactly(wholeDay());
    }

    @Test
    void partOfADayOffCancelsOnlyWhatOverlapsIt() {
        Appointment before = booked(ARIAS, DAY.atTime(10, 30));   // ends 11:00, where the absence starts
        Appointment inside = booked(ARIAS, DAY.atTime(11, 45));
        Appointment across = booked(ARIAS, DAY.atTime(12, 45));   // starts inside, ends after
        Appointment after = booked(ARIAS, DAY.atTime(13, 0));     // starts where the absence ends
        appointments.given(before, inside, across, after);

        RecordTimeOffUseCase.Result result = recordTimeOff.handle(new RecordTimeOffUseCase.Command(ARIAS, between(11, 13)));

        assertThat(result.cancelled()).extracting(Appointment::id).containsExactlyInAnyOrder(inside.id(), across.id());
        assertThat(statusOf(before)).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(statusOf(after)).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void anAppointmentWhosePatientArrivedIsReportedAndNotCancelled() {
        Appointment arrived = booked(ARIAS, DAY.atTime(9, 0)).checkedIn(DAY.atTime(8, 55));
        Appointment notYet = booked(ARIAS, DAY.atTime(10, 0));
        appointments.given(arrived, notYet);

        RecordTimeOffUseCase.Result result = recordTimeOff.handle(new RecordTimeOffUseCase.Command(ARIAS, wholeDay()));

        assertThat(result.needsManualHandling()).extracting(Appointment::id).containsExactly(arrived.id());
        assertThat(result.cancelled()).extracting(Appointment::id).containsExactly(notYet.id());
        assertThat(statusOf(arrived)).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void anotherPractitionersAppointmentsAreLeftAlone() {
        Appointment benitez = booked(BENITEZ, DAY.atTime(10, 0));
        appointments.given(benitez);

        RecordTimeOffUseCase.Result result = recordTimeOff.handle(new RecordTimeOffUseCase.Command(ARIAS, wholeDay()));

        assertThat(result.cancelled()).isEmpty();
        assertThat(statusOf(benitez)).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void theNextDayIsLeftAlone() {
        Appointment nextDay = booked(ARIAS, DAY.plusDays(1).atStartOfDay());
        appointments.given(nextDay);

        recordTimeOff.handle(new RecordTimeOffUseCase.Command(ARIAS, wholeDay()));

        assertThat(statusOf(nextDay)).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void anUnknownPractitionerIsRejectedAndNothingIsCancelled() {
        PractitionerId stranger = PractitionerId.newId();
        practitioners.givenUnknown(stranger);
        Appointment appointment = booked(stranger, DAY.atTime(10, 0));
        appointments.given(appointment);

        assertThatThrownBy(() -> recordTimeOff.handle(new RecordTimeOffUseCase.Command(stranger, wholeDay())))
                .isInstanceOf(UnknownPractitionerException.class);

        assertThat(statusOf(appointment)).isEqualTo(AppointmentStatus.BOOKED);
    }

    // --- not for a day already gone (D-38) ----------------------------------------------

    @Test
    void anAbsenceOnADayAlreadyGoneIsRefused() {
        TimeSlot yesterday = new TimeSlot(DAY.minusDays(1).atStartOfDay(), Duration.ofDays(1));

        assertThatThrownBy(() -> recordTimeOff.handle(new RecordTimeOffUseCase.Command(ARIAS, yesterday)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("day already gone");
    }

    @Test
    void todayIsAcceptedWholeEvenThoughItsMorningHasPassed() {
        Clock atNoon = Clock.fixed(DAY.atTime(12, 0).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant(),
                ZoneId.of("America/Argentina/Buenos_Aires"));
        Appointment afternoon = booked(ARIAS, DAY.atTime(15, 0));
        appointments.given(afternoon);

        RecordTimeOffUseCase.Result result = new RecordTimeOffUseCase(appointments, practitioners, atNoon)
                .handle(new RecordTimeOffUseCase.Command(ARIAS, wholeDay()));

        assertThat(result.cancelled()).extracting(Appointment::id).containsExactly(afternoon.id());
        assertThat(statusOf(afternoon)).isEqualTo(AppointmentStatus.CANCELLED);
    }
}
