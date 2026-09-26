package com.clinic.schedule.appointment.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.application.port.ArrivalPort;
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
 * <p>A patient who already came into a queue with it stays there, kept for nobody: the arrivals
 * are told, in the same transaction (D-64).
 *
 * <p>It takes the id directly instead of a {@code Command}: a single value needs no wrapper.
 */
@Service
public class CancelAppointmentUseCase {

    private final AppointmentRepository appointments;
    private final ArrivalPort arrivals;

    public CancelAppointmentUseCase(AppointmentRepository appointments, ArrivalPort arrivals) {
        this.appointments = appointments;
        this.arrivals = arrivals;
    }

    @Transactional
    public Appointment handle(AppointmentId id) {
        Appointment appointment = appointments.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));

        Appointment cancelled = appointment.cancelled();
        appointments.save(cancelled);
        arrivals.appointmentCancelled(id);
        return cancelled;
    }
}
