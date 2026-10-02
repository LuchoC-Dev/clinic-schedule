package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.port.PatientRegistrationPort;
import com.clinic.schedule.appointment.application.port.PatientRegistrationPort.NewPatient;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingChannel;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * Books an appointment for a patient who has never been to the practice: registers them and books
 * them in one transaction (D-37, D-75). A booking that is refused — the time was taken, the
 * practitioner does not work then — takes the patient back with it, and a patient the patient module
 * refuses books nothing; either way nothing is left half done.
 *
 * <p>The booking itself is {@link BookAppointmentUseCase}'s, which joins this transaction: the rules
 * are the same for a new patient as for anyone.
 *
 * <p>Its only caller, the booking screen, gives every attempt an id of its own, so a retry with an id
 * already booked — which {@code BookAppointmentUseCase} answers for the API (D-19) — does not happen
 * here, and is not guarded against.
 */
@Service
public class BookNewPatientUseCase {

    private final PatientRegistrationPort patients;
    private final BookAppointmentUseCase book;

    public BookNewPatientUseCase(PatientRegistrationPort patients, BookAppointmentUseCase book) {
        this.patients = patients;
        this.book = book;
    }

    @Transactional
    public Appointment handle(Command command) {
        PatientId registered = patients.register(command.patient());
        return book.handle(new BookAppointmentUseCase.Command(command.id(), registered, command.practitionerId(),
                command.reasonId(), command.startsAt(), command.note(), BookingChannel.RECEPTION));
    }

    /** A booking as {@link BookAppointmentUseCase.Command}, with the patient to register in place of their id. */
    public record Command(
            AppointmentId id,
            NewPatient patient,
            PractitionerId practitionerId,
            UUID reasonId,
            LocalDateTime startsAt,
            String note) {
    }
}
