package com.clinic.schedule.appointment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * Cancels an appointment, which keeps it as a cancelled record.
 *
 * <p>It does not go through {@link com.clinic.schedule.appointment.domain.BookingService}
 * and does not read the agenda: cancelling frees a slot instead of taking one, so there is
 * nothing to check against. The appointment guards the rest by itself — a cancelled one
 * cannot be cancelled again.
 *
 * <p>It takes the id directly instead of a {@code Command}: a single value needs no wrapper.
 */
@Service
public class CancelAppointmentUseCase {

    private final AppointmentRepository appointments;

    public CancelAppointmentUseCase(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional
    public Appointment handle(AppointmentId id) {
        Appointment appointment = appointments.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));

        Appointment cancelled = appointment.cancelled();
        appointments.save(cancelled);
        return cancelled;
    }
}
