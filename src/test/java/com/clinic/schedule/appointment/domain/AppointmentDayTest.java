package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.exception.NotTheAppointmentDayException;

/**
 * What happens to an appointment on the day: the notice of a late arrival, and when its patient is
 * absent (D-06, D-61). Whether the patient arrived is not the appointment's any more, so the rules
 * that depend on it are {@link AppointmentDayService}'s, given the arrival (D-65). Plain rules, no
 * Spring.
 */
class AppointmentDayTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final AppointmentDayService DAY = new AppointmentDayService();
    private static final Optional<LocalDateTime> NOT_ARRIVED = Optional.empty();
    private static final Optional<LocalDateTime> ARRIVED = Optional.of(TEN.minusMinutes(5));

    private final Appointment booked = Appointment.book(AppointmentId.newId(), PatientId.newId(),
            PractitionerId.newId(), TimeSlot.of(TEN, 30), "consulta", null, BookingChannel.RECEPTION);

    // --- late arrival notice -----------------------------------------------------------

    @Test
    void aLateArrivalNoticeIsRecorded() {
        Appointment warned = DAY.lateArrivalNotice(booked, TEN, NOT_ARRIVED);

        assertThat(warned.hasLateArrivalNotice()).isTrue();
        assertThat(booked.hasLateArrivalNotice()).isFalse();
    }

    @Test
    void receptionIsNotWarnedTwice() {
        Appointment warned = DAY.lateArrivalNotice(booked, TEN, NOT_ARRIVED);

        assertThatThrownBy(() -> DAY.lateArrivalNotice(warned, TEN, NOT_ARRIVED))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void noLateArrivalIsNoticedOnceThePatientArrived() {
        assertThatThrownBy(() -> DAY.lateArrivalNotice(booked, TEN, ARRIVED))
                .isInstanceOf(BookingRuleViolationException.class)
                .hasMessageContaining("already arrived");
    }

    @Test
    void movingTheAppointmentDropsTheLateArrivalNotice() {
        // The notice was about the old time.
        Appointment moved = DAY.lateArrivalNotice(booked, TEN, NOT_ARRIVED).movedTo(TimeSlot.of(TEN.plusDays(1), 30));

        assertThat(moved.hasLateArrivalNotice()).isFalse();
    }

    @Test
    void anAppointmentWhosePatientArrivedIsNotMoved() {
        assertThatThrownBy(() -> new BookingService().reschedule(booked, TimeSlot.of(TEN.plusHours(2), 30), TEN,
                List.of(), ARRIVED)).isInstanceOf(BookingRuleViolationException.class)
                .hasMessageContaining("already arrived");
    }

    @Test
    void anAppointmentWhosePatientArrivedCanStillBeCancelledByHand() {
        // The manual handling the context asks for when a practitioner is absent: the appointment
        // does not even know the patient came.
        assertThat(booked.cancelled(Cancellations.byHand()).status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    // --- when the patient is absent (D-61) -------------------------------------------------

    @Test
    void withoutAnArrivalItIsDueThirtyMinutesAfterTheStart() {
        assertThat(DAY.isDueForAbsence(booked, TEN.plusMinutes(29), NOT_ARRIVED)).isFalse();
        assertThat(DAY.isDueForAbsence(booked, TEN.plusMinutes(30), NOT_ARRIVED)).isTrue();
        assertThat(DAY.isDueForAbsence(booked, TEN.plusHours(3), NOT_ARRIVED)).isTrue();
    }

    @Test
    void itIsNotDueBeforeItStarts() {
        assertThat(DAY.isDueForAbsence(booked, TEN.minusMinutes(1), NOT_ARRIVED)).isFalse();
    }

    @Test
    void afterALateArrivalNoticeItIsDueAtTheEndOfItsDay() {
        Appointment warned = DAY.lateArrivalNotice(booked, TEN, NOT_ARRIVED);
        LocalDateTime midnight = TEN.toLocalDate().plusDays(1).atStartOfDay();

        assertThat(DAY.isDueForAbsence(warned, TEN.plusMinutes(30), NOT_ARRIVED)).isFalse();
        assertThat(DAY.isDueForAbsence(warned, midnight.minusMinutes(1), NOT_ARRIVED)).isFalse();
        assertThat(DAY.isDueForAbsence(warned, midnight, NOT_ARRIVED)).isTrue();
    }

    @Test
    void onceThePatientArrivedItIsNeverDue() {
        assertThat(DAY.isDueForAbsence(booked, TEN.plusDays(30), ARRIVED)).isFalse();
        assertThatThrownBy(() -> DAY.markAbsent(booked, TEN.plusDays(1), ARRIVED))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void anAbsentAppointmentIsTheRecordThatThePatientDidNotCome() {
        Appointment absent = DAY.markAbsent(booked, TEN.plusMinutes(30), NOT_ARRIVED);

        assertThat(absent.status()).isEqualTo(AppointmentStatus.ABSENT);
        assertThat(absent.isBooked()).isFalse();
        assertThat(DAY.isDueForAbsence(absent, TEN.plusDays(1), NOT_ARRIVED)).isFalse();
    }

    @Test
    void aPatientIsNotMarkedAbsentWhileTheyMayStillCome() {
        assertThatThrownBy(() -> DAY.markAbsent(booked, TEN.plusMinutes(29), NOT_ARRIVED))
                .isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> DAY.markAbsent(DAY.lateArrivalNotice(booked, TEN, NOT_ARRIVED), TEN.plusHours(3),
                NOT_ARRIVED)).isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> DAY.markAbsent(booked.cancelled(Cancellations.byHand()), TEN.plusDays(1), NOT_ARRIVED))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void nothingMoreHappensToAnAbsentAppointment() {
        Appointment absent = DAY.markAbsent(booked, TEN.plusMinutes(30), NOT_ARRIVED);

        assertThatThrownBy(() -> absent.cancelled(Cancellations.byHand())).isInstanceOf(BookingRuleViolationException.class)
                .hasMessageContaining("absent");
        assertThatThrownBy(() -> DAY.lateArrivalNotice(absent, TEN.plusMinutes(40), NOT_ARRIVED))
                .isInstanceOf(BookingRuleViolationException.class);
        assertThatThrownBy(() -> absent.movedTo(TimeSlot.of(TEN.plusDays(1), 30)))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void aCancelledAppointmentIsNeverDue() {
        assertThat(DAY.isDueForAbsence(booked.cancelled(Cancellations.byHand()), TEN.plusDays(1), NOT_ARRIVED))
                .isFalse();
    }

    // --- only on the day of the appointment (D-38) --------------------------------------

    @Test
    void aLateArrivalIsOnlyNoticedOnTheDay() {
        assertThatThrownBy(() -> DAY.lateArrivalNotice(booked, TEN.minusDays(1), NOT_ARRIVED))
                .isInstanceOf(NotTheAppointmentDayException.class);
        assertThat(DAY.lateArrivalNotice(booked, TEN.toLocalDate().atStartOfDay(), NOT_ARRIVED)
                .hasLateArrivalNotice()).isTrue();
    }
}
