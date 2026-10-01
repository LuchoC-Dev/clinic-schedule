package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.application.port.ArrivalPort;
import com.clinic.schedule.appointment.application.port.CurrentUserPort;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.Cancellation;
import com.clinic.schedule.appointment.domain.CancellationRequester;

/**
 * Reception cancels an appointment, which keeps it as a cancelled record with who cancelled it,
 * when, who asked for it and reception's note (D-76).
 *
 * <p>It does not go through {@link com.clinic.schedule.appointment.domain.BookingService}
 * and does not read the agenda: cancelling frees a slot instead of taking one, so there is
 * nothing to check against. The appointment guards the rest by itself — a cancelled one
 * cannot be cancelled again.
 *
 * <p>A patient who already came into a queue with it stays there, kept for nobody: the arrivals
 * are told, in the same transaction (D-64).
 */
@Service
public class CancelAppointmentUseCase {

    private final AppointmentRepository appointments;
    private final ArrivalPort arrivals;
    private final CurrentUserPort currentUser;
    private final Clock clock;

    public CancelAppointmentUseCase(AppointmentRepository appointments, ArrivalPort arrivals,
            CurrentUserPort currentUser, Clock clock) {
        this.appointments = appointments;
        this.arrivals = arrivals;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException when nobody is said to have asked, or the note is too long */
    @Transactional
    public Appointment handle(Command command) {
        Appointment appointment = appointments.findById(command.id())
                .orElseThrow(() -> new AppointmentNotFoundException(command.id()));

        Appointment cancelled = appointment.cancelled(Cancellation.byHand(LocalDateTime.now(clock),
                currentUser.username(), command.requestedBy(), command.note()));
        appointments.save(cancelled);
        arrivals.appointmentCancelled(command.id());
        return cancelled;
    }

    /** @param note reception's own words, or null */
    public record Command(AppointmentId id, CancellationRequester requestedBy, String note) {
    }
}
