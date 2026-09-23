package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * What happens to an appointment on the day: the check-in, the notice of a late arrival, and
 * when it becomes overdue (D-06). Plain rules, no Spring.
 */
class AppointmentDayTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);

    private final Appointment booked = Appointment.book(AppointmentId.newId(), PatientId.newId(),
            PractitionerId.newId(), TimeSlot.of(TEN, 30), "consulta", null);

    // --- check-in ----------------------------------------------------------------------

    @Test
    void checkingInRecordsTheTime() {
        Appointment checkedIn = booked.checkedIn(TEN.minusMinutes(5));

        assertThat(checkedIn.isCheckedIn()).isTrue();
        assertThat(checkedIn.checkedInAt()).isEqualTo(TEN.minusMinutes(5));
        assertThat(checkedIn.isBooked()).isTrue();
        assertThat(booked.isCheckedIn()).isFalse();
    }

    @Test
    void aPatientCannotCheckInTwice() {
        Appointment checkedIn = booked.checkedIn(TEN);

        assertThatThrownBy(() -> checkedIn.checkedIn(TEN.plusMinutes(1)))
                .isInstanceOf(BookingRuleViolationException.class)
                .hasMessageContaining("already arrived");
    }

    @Test
    void aCancelledAppointmentCannotBeCheckedIn() {
        assertThatThrownBy(() -> booked.cancelled().checkedIn(TEN))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void aCheckInNeedsItsTime() {
        assertThatIllegalArgumentException().isThrownBy(() -> booked.checkedIn(null));
    }

    @Test
    void anAppointmentWhosePatientArrivedCannotBeMoved() {
        Appointment checkedIn = booked.checkedIn(TEN);

        assertThatThrownBy(() -> checkedIn.movedTo(TimeSlot.of(TEN.plusHours(2), 30)))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void anAppointmentWhosePatientArrivedCanStillBeCancelledByHand() {
        // The manual handling the context asks for when a practitioner is absent.
        Appointment cancelled = booked.checkedIn(TEN).cancelled();

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.checkedInAt()).isEqualTo(TEN);
    }

    // --- late arrival notice -----------------------------------------------------------

    @Test
    void suspendingTheAutomaticCancellationIsRecorded() {
        Appointment suspended = booked.withAutoCancellationSuspended(TEN);

        assertThat(suspended.isAutoCancellationSuspended()).isTrue();
        assertThat(booked.isAutoCancellationSuspended()).isFalse();
    }

    @Test
    void itCannotBeSuspendedTwice() {
        Appointment suspended = booked.withAutoCancellationSuspended(TEN);

        assertThatThrownBy(() -> suspended.withAutoCancellationSuspended(TEN))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void itCannotBeSuspendedOnceThePatientArrived() {
        Appointment checkedIn = booked.checkedIn(TEN);

        assertThatThrownBy(() -> checkedIn.withAutoCancellationSuspended(TEN))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void aPatientWhoWarnedTheyWouldBeLateCanStillCheckIn() {
        Appointment arrived = booked.withAutoCancellationSuspended(TEN).checkedIn(TEN.plusHours(1));

        assertThat(arrived.isCheckedIn()).isTrue();
    }

    @Test
    void movingTheAppointmentDropsTheLateArrivalNotice() {
        // The notice was about the old time.
        Appointment moved = booked.withAutoCancellationSuspended(TEN).movedTo(TimeSlot.of(TEN.plusDays(1), 30));

        assertThat(moved.isAutoCancellationSuspended()).isFalse();
    }

    // --- when it is overdue ------------------------------------------------------------

    @Test
    void withoutACheckInItIsDueThirtyMinutesAfterTheStart() {
        assertThat(booked.isDueForAutoCancellation(TEN.plusMinutes(29))).isFalse();
        assertThat(booked.isDueForAutoCancellation(TEN.plusMinutes(30))).isTrue();
        assertThat(booked.isDueForAutoCancellation(TEN.plusHours(3))).isTrue();
    }

    @Test
    void itIsNotDueBeforeItStarts() {
        assertThat(booked.isDueForAutoCancellation(TEN.minusMinutes(1))).isFalse();
    }

    @Test
    void afterALateArrivalNoticeItIsDueTwentyFourHoursAfterTheStart() {
        Appointment suspended = booked.withAutoCancellationSuspended(TEN);

        assertThat(suspended.isDueForAutoCancellation(TEN.plusMinutes(30))).isFalse();
        assertThat(suspended.isDueForAutoCancellation(TEN.plusHours(24).minusMinutes(1))).isFalse();
        assertThat(suspended.isDueForAutoCancellation(TEN.plusHours(24))).isTrue();
    }

    @Test
    void onceThePatientCheckedInItIsNeverDue() {
        Appointment checkedIn = booked.checkedIn(TEN.plusMinutes(10));

        assertThat(checkedIn.isDueForAutoCancellation(TEN.plusDays(30))).isFalse();
    }

    @Test
    void aCancelledAppointmentIsNeverDue() {
        assertThat(booked.cancelled().isDueForAutoCancellation(TEN.plusDays(1))).isFalse();
    }

    // --- only on the day of the appointment (D-38) --------------------------------------

    @Test
    void aPatientCannotCheckInOnAnotherDay() {
        assertThatThrownBy(() -> booked.checkedIn(TEN.minusDays(1)))
                .isInstanceOf(NotTheAppointmentDayException.class);
        assertThatThrownBy(() -> booked.checkedIn(TEN.plusDays(1)))
                .isInstanceOf(NotTheAppointmentDayException.class);
    }

    @Test
    void aLateArrivalIsOnlyNoticedOnTheDay() {
        assertThatThrownBy(() -> booked.withAutoCancellationSuspended(TEN.minusDays(1)))
                .isInstanceOf(NotTheAppointmentDayException.class);
        assertThat(booked.withAutoCancellationSuspended(TEN.toLocalDate().atStartOfDay())
                .isAutoCancellationSuspended()).isTrue();
    }
}
