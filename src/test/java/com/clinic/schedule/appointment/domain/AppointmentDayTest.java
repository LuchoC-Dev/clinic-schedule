package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.exception.NotTheAppointmentDayException;

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
    void aLateArrivalNoticeIsRecorded() {
        Appointment warned = booked.withLateArrivalNotice(TEN);

        assertThat(warned.hasLateArrivalNotice()).isTrue();
        assertThat(booked.hasLateArrivalNotice()).isFalse();
    }

    @Test
    void receptionIsNotWarnedTwice() {
        Appointment warned = booked.withLateArrivalNotice(TEN);

        assertThatThrownBy(() -> warned.withLateArrivalNotice(TEN))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void noLateArrivalIsNoticedOnceThePatientArrived() {
        Appointment checkedIn = booked.checkedIn(TEN);

        assertThatThrownBy(() -> checkedIn.withLateArrivalNotice(TEN))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void aPatientWhoWarnedTheyWouldBeLateCanStillCheckIn() {
        Appointment arrived = booked.withLateArrivalNotice(TEN).checkedIn(TEN.plusHours(1));

        assertThat(arrived.isCheckedIn()).isTrue();
    }

    @Test
    void movingTheAppointmentDropsTheLateArrivalNotice() {
        // The notice was about the old time.
        Appointment moved = booked.withLateArrivalNotice(TEN).movedTo(TimeSlot.of(TEN.plusDays(1), 30));

        assertThat(moved.hasLateArrivalNotice()).isFalse();
    }

    // --- when the patient is absent (D-61) -------------------------------------------------

    @Test
    void withoutACheckInItIsDueThirtyMinutesAfterTheStart() {
        assertThat(booked.isDueForAbsence(TEN.plusMinutes(29))).isFalse();
        assertThat(booked.isDueForAbsence(TEN.plusMinutes(30))).isTrue();
        assertThat(booked.isDueForAbsence(TEN.plusHours(3))).isTrue();
    }

    @Test
    void itIsNotDueBeforeItStarts() {
        assertThat(booked.isDueForAbsence(TEN.minusMinutes(1))).isFalse();
    }

    @Test
    void afterALateArrivalNoticeItIsDueAtTheEndOfItsDay() {
        Appointment warned = booked.withLateArrivalNotice(TEN);
        LocalDateTime midnight = TEN.toLocalDate().plusDays(1).atStartOfDay();

        assertThat(warned.isDueForAbsence(TEN.plusMinutes(30))).isFalse();
        assertThat(warned.isDueForAbsence(midnight.minusMinutes(1))).isFalse();
        assertThat(warned.isDueForAbsence(midnight)).isTrue();
    }

    @Test
    void anAbsentAppointmentIsTheRecordThatThePatientDidNotCome() {
        Appointment absent = booked.markedAbsent(TEN.plusMinutes(30));

        assertThat(absent.status()).isEqualTo(AppointmentStatus.ABSENT);
        assertThat(absent.isBooked()).isFalse();
        assertThat(absent.isDueForAbsence(TEN.plusDays(1))).isFalse();
    }

    @Test
    void aPatientIsNotMarkedAbsentWhileTheyMayStillCome() {
        assertThatThrownBy(() -> booked.markedAbsent(TEN.plusMinutes(29)))
                .isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> booked.withLateArrivalNotice(TEN).markedAbsent(TEN.plusHours(3)))
                .isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> booked.checkedIn(TEN).markedAbsent(TEN.plusDays(1)))
                .isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> booked.cancelled().markedAbsent(TEN.plusDays(1)))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void nothingMoreHappensToAnAbsentAppointment() {
        Appointment absent = booked.markedAbsent(TEN.plusMinutes(30));

        assertThatThrownBy(absent::cancelled).isInstanceOf(BookingRuleViolationException.class)
                .hasMessageContaining("absent");
        assertThatThrownBy(() -> absent.checkedIn(TEN.plusMinutes(40)))
                .isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> absent.withLateArrivalNotice(TEN.plusMinutes(40)))
                .isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> absent.movedTo(TimeSlot.of(TEN.plusDays(1), 30)))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void onceThePatientCheckedInItIsNeverDue() {
        Appointment checkedIn = booked.checkedIn(TEN.plusMinutes(10));

        assertThat(checkedIn.isDueForAbsence(TEN.plusDays(30))).isFalse();
    }

    @Test
    void aCancelledAppointmentIsNeverDue() {
        assertThat(booked.cancelled().isDueForAbsence(TEN.plusDays(1))).isFalse();
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
        assertThatThrownBy(() -> booked.withLateArrivalNotice(TEN.minusDays(1)))
                .isInstanceOf(NotTheAppointmentDayException.class);
        assertThat(booked.withLateArrivalNotice(TEN.toLocalDate().atStartOfDay())
                .hasLateArrivalNotice()).isTrue();
    }
}
