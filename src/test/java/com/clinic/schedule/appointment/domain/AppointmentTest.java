package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;

class AppointmentTest {

    private static final AppointmentId ID = AppointmentId.newId();
    private static final PatientId PATIENT = PatientId.newId();
    private static final PractitionerId PRACTITIONER = PractitionerId.newId();
    private static final TimeSlot TEN_THIRTY = TimeSlot.of(LocalDateTime.of(2026, 3, 10, 10, 0), 30);

    private Appointment booked() {
        return Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null, BookingChannel.RECEPTION);
    }

    @Test
    void aNewAppointmentIsBookedAndCarriesTheGivenId() {
        Appointment appointment = booked();

        assertThat(appointment.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(appointment.isBooked()).isTrue();
        assertThat(appointment.id()).isEqualTo(ID);
        assertThat(appointment.note()).isNull();
    }

    @Test
    void theReasonIsRequiredAndTrimmed() {
        assertThat(Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "  consulta  ", null,
                BookingChannel.RECEPTION).reason())
                .isEqualTo("consulta");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "   ", null,
                        BookingChannel.RECEPTION));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, null, null,
                        BookingChannel.RECEPTION));
    }

    @Test
    void anAppointmentNeedsIdPatientPractitionerAndSlot() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(null, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null,
                        BookingChannel.RECEPTION));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, null, PRACTITIONER, TEN_THIRTY, "consulta", null,
                        BookingChannel.RECEPTION));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, null, TEN_THIRTY, "consulta", null,
                        BookingChannel.RECEPTION));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, PRACTITIONER, null, "consulta", null,
                        BookingChannel.RECEPTION));
    }

    @Test
    void cancellingKeepsTheAppointmentAsACancelledRecord() {
        Appointment cancelled = booked().cancelled(Cancellations.byHand());

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.isBooked()).isFalse();
        assertThat(cancelled.patientId()).isEqualTo(PATIENT);
        assertThat(cancelled.slot()).isEqualTo(TEN_THIRTY);
        assertThat(cancelled.reason()).isEqualTo("consulta");
    }

    @Test
    void cancellingLeavesTheOriginalUntouched() {
        Appointment appointment = booked();

        appointment.cancelled(Cancellations.byHand());

        assertThat(appointment.isBooked()).isTrue();
    }

    @Test
    void aCancelledAppointmentCannotBeCancelledAgain() {
        Appointment cancelled = booked().cancelled(Cancellations.byHand());

        assertThatThrownBy(() -> cancelled.cancelled(Cancellations.byHand()))
                .isInstanceOf(BookingRuleViolationException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void movingReturnsTheAppointmentOnTheNewSlotAndKeepsNoHistory() {
        Appointment appointment = booked();
        TimeSlot newSlot = TimeSlot.of(LocalDateTime.of(2026, 3, 11, 16, 0), 45);

        Appointment moved = appointment.movedTo(newSlot);

        assertThat(moved.slot()).isEqualTo(newSlot);
        assertThat(moved.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(moved.id()).isEqualTo(ID);
        // The original is left alone, so a rejected move changes nothing.
        assertThat(appointment.slot()).isEqualTo(TEN_THIRTY);
    }

    @Test
    void aCancelledAppointmentKeepsWhatWasRecordedOfItsCancellation() {
        Cancellation cancellation = Cancellations.byHand();

        assertThat(booked().cancellation()).isEmpty();
        assertThat(booked().cancelled(cancellation).cancellation()).contains(cancellation);
        assertThatIllegalArgumentException().isThrownBy(() -> booked().cancelled(null));
    }

    @Test
    void onlyACancelledAppointmentIsRestoredWithACancellation() {
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(ID, PATIENT, PRACTITIONER,
                TEN_THIRTY, "consulta", null, AppointmentStatus.BOOKED, false, Cancellations.byHand(),
                        BookingChannel.RECEPTION));
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(ID, PATIENT, PRACTITIONER,
                TEN_THIRTY, "consulta", null, AppointmentStatus.ABSENT, false, Cancellations.byHand(),
                        BookingChannel.RECEPTION));
        // Cancelled before anything was recorded of it (D-76).
        assertThat(Appointment.restore(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null,
                AppointmentStatus.CANCELLED, false, null, BookingChannel.RECEPTION).cancellation()).isEmpty();
    }

    @Test
    void aCancelledAppointmentCannotBeMoved() {
        Appointment cancelled = booked().cancelled(Cancellations.byHand());

        assertThatThrownBy(() -> cancelled.movedTo(TEN_THIRTY))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void aRestoredAppointmentIsCheckedLikeANewOne() {
        // The aggregate does not trust the persistence adapter either.
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(
                ID, null, PRACTITIONER, TEN_THIRTY, "consulta", null, AppointmentStatus.BOOKED, false, null,
                        BookingChannel.RECEPTION));
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(
                ID, PATIENT, PRACTITIONER, TEN_THIRTY, "  ", null, AppointmentStatus.BOOKED, false, null,
                        BookingChannel.RECEPTION));
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(
                null, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null, AppointmentStatus.BOOKED, false, null,
                        BookingChannel.RECEPTION));
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(
                ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null, null, false, null, BookingChannel.RECEPTION));
    }

    @Test
    void appointmentsAreTheSameOnlyWhenTheirIdMatches() {
        Appointment stored = Appointment.restore(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null,
                AppointmentStatus.BOOKED, false, null, BookingChannel.RECEPTION);
        Appointment other = Appointment.restore(AppointmentId.newId(), PATIENT, PRACTITIONER, TEN_THIRTY,
                "consulta", null, AppointmentStatus.BOOKED, false, null, BookingChannel.RECEPTION);

        // Identity exists from creation, so this is always answerable.
        assertThat(stored.isSameAs(booked())).isTrue();
        assertThat(stored.isSameAs(other)).isFalse();
    }

    @Test
    void anIdIsRequiredAndKeepsItsValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AppointmentId(null));
        assertThat(AppointmentId.newId()).isNotEqualTo(AppointmentId.newId());
        assertThat(AppointmentId.of("018f1d2c-0000-7000-8000-000000000001").value())
                .hasToString("018f1d2c-0000-7000-8000-000000000001");
    }

    @Test
    void aNoteFitsItsColumn() {
        TimeSlot slot = TimeSlot.of(LocalDateTime.of(2026, 3, 10, 10, 0), 30);

        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(AppointmentId.newId(),
                        PatientId.newId(), PractitionerId.newId(), slot, "consulta", "x".repeat(501),
                        AppointmentStatus.BOOKED, false, null, BookingChannel.RECEPTION))
                .withMessageContaining("at most 500");
    }

    /** Where it was booked stays with it, through whatever happens to it (D-82). */
    @Test
    void anAppointmentKeepsWhereItWasBooked() {
        Appointment online = Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null,
                BookingChannel.WEB);

        assertThat(online.bookedVia()).isEqualTo(BookingChannel.WEB);
        assertThat(online.cancelled(Cancellation.byHand(TEN_THIRTY.startsAt(), "recepcion",
                CancellationRequester.PATIENT, null)).bookedVia()).isEqualTo(BookingChannel.WEB);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null, null));
    }
}
