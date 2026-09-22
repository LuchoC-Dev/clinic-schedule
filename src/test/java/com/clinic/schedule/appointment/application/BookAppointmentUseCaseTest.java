package com.clinic.schedule.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.PatientAlreadyBookedException;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.PractitionerId;

class BookAppointmentUseCaseTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final PatientId ANA = new PatientId(1L);
    private static final PatientId LUIS = new PatientId(2L);
    private static final PractitionerId ARIAS = new PractitionerId(7L);
    private static final PractitionerId BENITEZ = new PractitionerId(8L);

    private final InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
    private final BookAppointmentUseCase useCase =
            new BookAppointmentUseCase(appointments, new BookingService());

    private BookAppointmentUseCase.Command command(AppointmentId id, PatientId patient,
            PractitionerId practitioner, int atMinute, int minutes) {
        return new BookAppointmentUseCase.Command(id, patient, practitioner, TEN.plusMinutes(atMinute), minutes,
                "consulta", null);
    }

    @Test
    void booksAndStoresTheAppointment() {
        AppointmentId id = AppointmentId.newId();

        Appointment booked = useCase.handle(command(id, ANA, ARIAS, 0, 30));

        assertThat(booked.isBooked()).isTrue();
        assertThat(appointments.findById(id)).contains(booked);
    }

    @Test
    void aRetryWithTheSameIdDoesNotBookTwice() {
        AppointmentId id = AppointmentId.newId();
        BookAppointmentUseCase.Command sameRequest = command(id, ANA, ARIAS, 0, 30);

        Appointment first = useCase.handle(sameRequest);
        Appointment retry = useCase.handle(sameRequest);

        assertThat(retry.isSameAs(first)).isTrue();
        assertThat(appointments.storedCount()).isEqualTo(1);
        assertThat(appointments.saveCount()).isEqualTo(1);
    }

    @Test
    void rejectsAnOverlapWithThePractitionersAgenda() {
        useCase.handle(command(AppointmentId.newId(), LUIS, ARIAS, 0, 30));

        assertThatThrownBy(() -> useCase.handle(command(AppointmentId.newId(), ANA, ARIAS, 15, 30)))
                .isInstanceOf(PractitionerAlreadyBookedException.class);

        assertThat(appointments.storedCount()).isEqualTo(1);
    }

    @Test
    void rejectsThePatientBeingBookedWithTwoPractitionersAtOnce() {
        useCase.handle(command(AppointmentId.newId(), ANA, BENITEZ, 0, 30));

        assertThatThrownBy(() -> useCase.handle(command(AppointmentId.newId(), ANA, ARIAS, 15, 30)))
                .isInstanceOf(PatientAlreadyBookedException.class);
    }

    @Test
    void acceptsBackToBackAppointmentsForTheSamePractitioner() {
        useCase.handle(command(AppointmentId.newId(), LUIS, ARIAS, 0, 30));
        useCase.handle(command(AppointmentId.newId(), ANA, ARIAS, 30, 30));

        assertThat(appointments.storedCount()).isEqualTo(2);
    }

    @Test
    void aCancelledAppointmentDoesNotBlockItsSlot() {
        AppointmentId first = AppointmentId.newId();
        useCase.handle(command(first, LUIS, ARIAS, 0, 30));
        new CancelAppointmentUseCase(appointments).handle(first);

        Appointment second = useCase.handle(command(AppointmentId.newId(), ANA, ARIAS, 0, 30));

        assertThat(second.isBooked()).isTrue();
    }

    @Test
    void anotherDayAtTheSameTimeIsFree() {
        useCase.handle(command(AppointmentId.newId(), LUIS, ARIAS, 0, 30));

        Appointment nextDay = useCase.handle(new BookAppointmentUseCase.Command(AppointmentId.newId(), ANA,
                ARIAS, TEN.plusDays(1), 30, "consulta", null));

        assertThat(nextDay.isBooked()).isTrue();
    }
}
