package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.application.port.CurrentUserPort;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.Cancellation;

/**
 * The patient cancels their appointment (D-83): from their web account, recorded as made by it, or from the
 * link in an email, made by whoever held it. Either way the patient asked, and there is no note. Which
 * appointments, and until when, is the portal's to decide; this records it as reception's cancellations are
 * recorded. The arrivals are not told, as they are of reception's (D-64): a patient cancels a day ahead at
 * least, so they cannot be waiting in a queue with it.
 */
@Service
public class CancelForPatientUseCase {

    private final AppointmentRepository appointments;
    private final CurrentUserPort currentUser;
    private final Clock clock;

    public CancelForPatientUseCase(AppointmentRepository appointments, CurrentUserPort currentUser, Clock clock) {
        this.appointments = appointments;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** From the web account of the patient logged in. */
    @Transactional
    public Appointment onTheWeb(AppointmentId id) {
        return cancel(id, Cancellation.onTheWeb(LocalDateTime.now(clock), currentUser.username()));
    }

    /** From an email's link, with nobody logged in. */
    @Transactional
    public Appointment byEmailLink(AppointmentId id) {
        return cancel(id, Cancellation.byEmailLink(LocalDateTime.now(clock)));
    }

    private Appointment cancel(AppointmentId id, Cancellation cancellation) {
        Appointment cancelled = appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException(id))
                .cancelled(cancellation);
        appointments.save(cancelled);
        return cancelled;
    }
}
