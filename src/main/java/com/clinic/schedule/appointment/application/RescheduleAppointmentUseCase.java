package com.clinic.schedule.appointment.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.BookingService;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Moves an existing appointment to another start time, keeping its duration.
 *
 * <p>The duration is the one it was booked with, not the reason's current one: an
 * appointment records what was booked, and changing a reason's duration does not move the
 * agenda under reception's feet (D-28). So only the start changes, and the new slot is
 * checked against the practitioner's hours and the rest of the agenda.
 *
 * <p>Unlike booking, this one is not idempotent and cannot be: asking twice for the same
 * move is indistinguishable from asking for it again.
 */
@Service
public class RescheduleAppointmentUseCase {

    private final AppointmentRepository appointments;
    private final PractitionerSchedulePort practitioners;
    private final BookingService bookingService;
    private final AgendaReader agenda;
    private final Clock clock;

    public RescheduleAppointmentUseCase(AppointmentRepository appointments, PractitionerSchedulePort practitioners,
            BookingService bookingService, Clock clock) {
        this.appointments = appointments;
        this.practitioners = practitioners;
        this.bookingService = bookingService;
        this.agenda = new AgendaReader(appointments);
        this.clock = clock;
    }

    @Transactional
    public Appointment handle(Command command) {
        Appointment appointment = appointments.findById(command.id())
                .orElseThrow(() -> new AppointmentNotFoundException(command.id()));

        TimeSlot newSlot = new TimeSlot(command.startsAt(), appointment.slot().duration());
        if (!practitioners.worksDuring(appointment.practitionerId(), newSlot)) {
            throw new OutsideWorkingHoursException(appointment.practitionerId(), newSlot);
        }

        Appointment moved = bookingService.reschedule(appointment, newSlot, LocalDateTime.now(clock),
                agenda.around(newSlot, appointment.practitionerId(), appointment.patientId()));

        appointments.save(moved);
        return moved;
    }

    public record Command(AppointmentId id, LocalDateTime startsAt) {
    }
}
