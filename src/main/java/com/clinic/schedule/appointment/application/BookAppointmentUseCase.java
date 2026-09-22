package com.clinic.schedule.appointment.application;

import java.time.LocalDateTime;
import java.util.Optional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Books an appointment: read what could collide, let the domain decide, store the result.
 *
 * <p>Retrying is safe. An id that already exists means this request already went through —
 * reception double-clicked, or the connection dropped after the appointment was stored — so
 * the existing appointment is returned instead of booking a second one. That is what the
 * caller-assigned id buys (D-19).
 *
 * <p>No Spring annotations yet: nothing implements {@link AppointmentRepository}, so there is
 * nothing to wire. The {@code @Service} and the transaction arrive with the persistence
 * adapter, in the same stage (D-20).
 */
public class BookAppointmentUseCase {

    private final AppointmentRepository appointments;
    private final BookingService bookingService;
    private final AgendaReader agenda;

    public BookAppointmentUseCase(AppointmentRepository appointments, BookingService bookingService) {
        this.appointments = appointments;
        this.bookingService = bookingService;
        this.agenda = new AgendaReader(appointments);
    }

    public Appointment handle(Command command) {
        Optional<Appointment> alreadyBooked = appointments.findById(command.id());
        if (alreadyBooked.isPresent()) {
            return alreadyBooked.get();
        }

        TimeSlot slot = TimeSlot.of(command.startsAt(), command.durationMinutes());
        Appointment booked = bookingService.book(
                command.id(),
                command.patientId(),
                command.practitionerId(),
                slot,
                command.reason(),
                command.note(),
                agenda.around(slot, command.practitionerId(), command.patientId()));

        appointments.save(booked);
        return booked;
    }

    /**
     * What reception fills in to book an appointment.
     *
     * <p>{@code id} comes from the caller so a retried request reuses it instead of booking
     * twice.
     *
     * <p>{@code durationMinutes} is given explicitly for now. Once the practitioner module
     * exists, the duration will be looked up from the reason and this field disappears
     * (D-08). The domain does not change when that happens, since it always worked with a
     * start and a duration.
     */
    public record Command(
            AppointmentId id,
            PatientId patientId,
            PractitionerId practitionerId,
            LocalDateTime startsAt,
            int durationMinutes,
            String reason,
            String note) {
    }
}
