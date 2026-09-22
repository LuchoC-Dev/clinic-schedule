package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class BookingPolicyTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final PatientId ANA = new PatientId(1L);
    private static final PatientId LUIS = new PatientId(2L);
    private static final PractitionerId ARIAS = new PractitionerId(7L);
    private static final PractitionerId BENITEZ = new PractitionerId(8L);

    private final BookingPolicy policy = new BookingPolicy();

    private Appointment appointment(PatientId patient, PractitionerId practitioner, int atMinute, int minutes) {
        return Appointment.book(AppointmentId.newId(), patient, practitioner,
                TimeSlot.of(TEN.plusMinutes(atMinute), minutes), "consulta", null);
    }

    private Appointment stored(PatientId patient, PractitionerId practitioner, int atMinute, int minutes) {
        return Appointment.restore(AppointmentId.newId(), patient, practitioner,
                TimeSlot.of(TEN.plusMinutes(atMinute), minutes), "consulta", null, AppointmentStatus.BOOKED);
    }

    @Test
    void anEmptyAgendaAcceptsAnything() {
        assertThatCode(() -> policy.check(appointment(ANA, ARIAS, 0, 30), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void thePractitionerCannotBeBookedTwiceAtOnce() {
        Appointment existing = stored(LUIS, ARIAS, 15, 30);

        assertThatThrownBy(() -> policy.check(appointment(ANA, ARIAS, 0, 30), List.of(existing)))
                .isInstanceOf(PractitionerAlreadyBookedException.class);
    }

    @Test
    void thePatientCannotBeInTwoPlacesAtOnce() {
        Appointment existing = stored(ANA, BENITEZ, 15, 30);

        assertThatThrownBy(() -> policy.check(appointment(ANA, ARIAS, 0, 30), List.of(existing)))
                .isInstanceOf(PatientAlreadyBookedException.class);
    }

    @Test
    void backToBackAppointmentsAreAccepted() {
        Appointment existing = stored(LUIS, ARIAS, 30, 30);

        assertThatCode(() -> policy.check(appointment(ANA, ARIAS, 0, 30), List.of(existing)))
                .doesNotThrowAnyException();
    }

    @Test
    void differentPatientsAndPractitionersAtTheSameTimeAreFine() {
        Appointment existing = stored(LUIS, BENITEZ, 0, 30);

        assertThatCode(() -> policy.check(appointment(ANA, ARIAS, 0, 30), List.of(existing)))
                .doesNotThrowAnyException();
    }

    @Test
    void cancelledAppointmentsDoNotBlockTheSlot() {
        Appointment cancelled = stored(LUIS, ARIAS, 0, 30).cancelled();

        assertThatCode(() -> policy.check(appointment(ANA, ARIAS, 0, 30), List.of(cancelled)))
                .doesNotThrowAnyException();
    }

    @Test
    void reschedulingDoesNotCollideWithTheAppointmentItself() {
        Appointment original = stored(ANA, ARIAS, 0, 30);
        // Moved by ten minutes, so the new slot overlaps its own previous one.
        Appointment moved = original.movedTo(TimeSlot.of(TEN.plusMinutes(10), 30));

        assertThatCode(() -> policy.check(moved, List.of(original)))
                .doesNotThrowAnyException();
    }

    @Test
    void thePractitionerRuleIsCheckedBeforeThePatientRule() {
        // Same patient and same practitioner: the practitioner clash is the useful message
        // for reception, because it is the one that blocks the agenda slot.
        Appointment existing = stored(ANA, ARIAS, 15, 30);

        assertThatThrownBy(() -> policy.check(appointment(ANA, ARIAS, 0, 30), List.of(existing)))
                .isInstanceOf(PractitionerAlreadyBookedException.class);
    }

    @Test
    void theWholeAgendaIsChecked() {
        List<Appointment> agenda = List.of(
                stored(LUIS, BENITEZ, 0, 30),
                stored(LUIS, ARIAS, 60, 30),
                stored(LUIS, ARIAS, 15, 30));

        assertThatThrownBy(() -> policy.check(appointment(ANA, ARIAS, 0, 30), agenda))
                .isInstanceOf(PractitionerAlreadyBookedException.class);
    }
}
