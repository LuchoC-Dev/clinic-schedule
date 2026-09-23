package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * These tests are about the guarantee {@link BookingService} adds on top of the pieces: you
 * cannot get an appointment out of the domain without it having been checked against the
 * agenda.
 */
class BookingServiceTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final PatientId ANA = PatientId.newId();
    private static final PatientId LUIS = PatientId.newId();
    private static final PractitionerId ARIAS = PractitionerId.newId();

    private final BookingService bookingService = new BookingService();

    private Appointment stored(PatientId patient, int atMinute, int minutes) {
        return Appointment.restore(AppointmentId.newId(), patient, ARIAS,
                TimeSlot.of(TEN.plusMinutes(atMinute), minutes), "consulta", null, AppointmentStatus.BOOKED, null, false);
    }

    @Test
    void bookingOnAFreeSlotReturnsABookedAppointment() {
        AppointmentId id = AppointmentId.newId();

        Appointment booked = bookingService.book(id, ANA, ARIAS, TimeSlot.of(TEN, 30), "consulta",
                "trae estudios", List.of());

        assertThat(booked.isBooked()).isTrue();
        assertThat(booked.id()).isEqualTo(id);
        assertThat(booked.patientId()).isEqualTo(ANA);
        assertThat(booked.reason()).isEqualTo("consulta");
        assertThat(booked.note()).isEqualTo("trae estudios");
    }

    @Test
    void bookingOnATakenSlotIsRejected() {
        List<Appointment> agenda = List.of(stored(LUIS, 15, 30));

        assertThatThrownBy(() -> bookingService.book(AppointmentId.newId(), ANA, ARIAS, TimeSlot.of(TEN, 30),
                "consulta", null, agenda))
                .isInstanceOf(PractitionerAlreadyBookedException.class);
    }

    @Test
    void bookingTwiceWithTheSameIdKeepsTheSameIdentity() {
        // What makes a retried request idempotent: the caller decides the id, so the second
        // attempt is recognizable as the same appointment instead of becoming a new one.
        AppointmentId id = AppointmentId.newId();

        Appointment first = bookingService.book(id, ANA, ARIAS, TimeSlot.of(TEN, 30), "consulta", null, List.of());
        Appointment retry = bookingService.book(id, ANA, ARIAS, TimeSlot.of(TEN, 30), "consulta", null, List.of());

        assertThat(retry.isSameAs(first)).isTrue();
    }

    @Test
    void reschedulingReturnsTheAppointmentOnItsNewSlot() {
        Appointment existing = stored(ANA, 0, 30);
        TimeSlot newSlot = TimeSlot.of(TEN.plusHours(3), 30);

        Appointment moved = bookingService.reschedule(existing, newSlot, List.of(existing));

        assertThat(moved.slot()).isEqualTo(newSlot);
        assertThat(moved.id()).isEqualTo(existing.id());
    }

    @Test
    void aRejectedRescheduleLeavesTheAppointmentOnItsOriginalSlot() {
        Appointment existing = stored(ANA, 0, 30);
        Appointment other = stored(LUIS, 120, 30);
        TimeSlot takenSlot = TimeSlot.of(TEN.plusMinutes(120), 30);

        assertThatThrownBy(() -> bookingService.reschedule(existing, takenSlot, List.of(existing, other)))
                .isInstanceOf(PractitionerAlreadyBookedException.class);

        assertThat(existing.slot()).isEqualTo(TimeSlot.of(TEN, 30));
    }

    @Test
    void aCancelledAppointmentCannotBeRescheduled() {
        Appointment cancelled = stored(ANA, 0, 30).cancelled();

        assertThatThrownBy(() -> bookingService.reschedule(cancelled, TimeSlot.of(TEN.plusHours(3), 30), List.of()))
                .isInstanceOf(BookingRuleViolationException.class);
    }
}
