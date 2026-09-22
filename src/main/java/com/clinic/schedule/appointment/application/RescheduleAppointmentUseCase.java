package com.clinic.schedule.appointment.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Moves an existing appointment to another slot, checking the new one against the agenda.
 *
 * <p>Unlike booking, this one is not idempotent and cannot be: asking twice for the same
 * move is indistinguishable from asking for it again, and the second attempt simply finds
 * the appointment already where it should be.
 */
@Service
public class RescheduleAppointmentUseCase {

    private final AppointmentRepository appointments;
    private final BookingService bookingService;
    private final AgendaReader agenda;

    public RescheduleAppointmentUseCase(AppointmentRepository appointments, BookingService bookingService) {
        this.appointments = appointments;
        this.bookingService = bookingService;
        this.agenda = new AgendaReader(appointments);
    }

    @Transactional
    public Appointment handle(Command command) {
        Appointment appointment = appointments.findById(command.id())
                .orElseThrow(() -> new AppointmentNotFoundException(command.id()));

        TimeSlot newSlot = TimeSlot.of(command.startsAt(), command.durationMinutes());
        Appointment moved = bookingService.reschedule(appointment, newSlot,
                agenda.around(newSlot, appointment.practitionerId(), appointment.patientId()));

        appointments.save(moved);
        return moved;
    }

    /**
     * The duration can change along with the time, because reception may be moving the
     * appointment to a longer or shorter reason. See {@link BookAppointmentUseCase.Command}
     * about why the duration is still given explicitly.
     */
    public record Command(
            AppointmentId id,
            LocalDateTime startsAt,
            int durationMinutes) {
    }
}
