package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class AppointmentTest {

    private static final AppointmentId ID = AppointmentId.newId();
    private static final PatientId PATIENT = new PatientId(1L);
    private static final PractitionerId PRACTITIONER = new PractitionerId(7L);
    private static final TimeSlot TEN_THIRTY = TimeSlot.of(LocalDateTime.of(2026, 3, 10, 10, 0), 30);

    private Appointment booked() {
        return Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null);
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
        assertThat(Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "  consulta  ", null).reason())
                .isEqualTo("consulta");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "   ", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, PRACTITIONER, TEN_THIRTY, null, null));
    }

    @Test
    void anAppointmentNeedsIdPatientPractitionerAndSlot() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(null, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, null, PRACTITIONER, TEN_THIRTY, "consulta", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, null, TEN_THIRTY, "consulta", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Appointment.book(ID, PATIENT, PRACTITIONER, null, "consulta", null));
    }

    @Test
    void cancellingKeepsTheAppointmentAsACancelledRecord() {
        Appointment cancelled = booked().cancelled();

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.isBooked()).isFalse();
        assertThat(cancelled.patientId()).isEqualTo(PATIENT);
        assertThat(cancelled.slot()).isEqualTo(TEN_THIRTY);
        assertThat(cancelled.reason()).isEqualTo("consulta");
    }

    @Test
    void cancellingLeavesTheOriginalUntouched() {
        Appointment appointment = booked();

        appointment.cancelled();

        assertThat(appointment.isBooked()).isTrue();
    }

    @Test
    void aCancelledAppointmentCannotBeCancelledAgain() {
        Appointment cancelled = booked().cancelled();

        assertThatThrownBy(cancelled::cancelled)
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
    void aCancelledAppointmentCannotBeMoved() {
        Appointment cancelled = booked().cancelled();

        assertThatThrownBy(() -> cancelled.movedTo(TEN_THIRTY))
                .isInstanceOf(BookingRuleViolationException.class);
    }

    @Test
    void aRestoredAppointmentIsCheckedLikeANewOne() {
        // The aggregate does not trust the persistence adapter either.
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(
                ID, null, PRACTITIONER, TEN_THIRTY, "consulta", null, AppointmentStatus.BOOKED));
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(
                ID, PATIENT, PRACTITIONER, TEN_THIRTY, "  ", null, AppointmentStatus.BOOKED));
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(
                null, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null, AppointmentStatus.BOOKED));
        assertThatIllegalArgumentException().isThrownBy(() -> Appointment.restore(
                ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null, null));
    }

    @Test
    void appointmentsAreTheSameOnlyWhenTheirIdMatches() {
        Appointment stored = Appointment.restore(ID, PATIENT, PRACTITIONER, TEN_THIRTY, "consulta", null,
                AppointmentStatus.BOOKED);
        Appointment other = Appointment.restore(AppointmentId.newId(), PATIENT, PRACTITIONER, TEN_THIRTY,
                "consulta", null, AppointmentStatus.BOOKED);

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
}
