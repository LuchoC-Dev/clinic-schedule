package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.NotYourAppointmentException;
import com.clinic.schedule.portal.application.port.AppointmentCancellationPort;
import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientAppointmentsPort;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.WebCancellationPolicy;

/**
 * The patient logged in cancels one of their appointments from their web account (D-83): any they have
 * booked, reception's too, up to {@code app.portal.cancel-hours-before} hours before it. Recorded as made by
 * their account and asked by the patient.
 */
@Service
public class CancelOwnAppointmentUseCase {

    private final CurrentPatientPort currentPatient;
    private final PatientAppointmentsPort appointments;
    private final AppointmentCancellationPort cancellations;
    private final Clock clock;
    private final int hoursBefore;

    public CancelOwnAppointmentUseCase(CurrentPatientPort currentPatient, PatientAppointmentsPort appointments,
            AppointmentCancellationPort cancellations, Clock clock,
            @Value("${app.portal.cancel-hours-before}") int hoursBefore) {
        this.currentPatient = currentPatient;
        this.appointments = appointments;
        this.cancellations = cancellations;
        this.clock = clock;
        this.hoursBefore = hoursBefore;
    }

    /**
     * @throws NotYourAppointmentException when it is not one of theirs
     * @throws com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException when it is not booked
     *         any more, or there is no time left
     */
    @Transactional
    public void handle(UUID appointmentId) {
        Appointment appointment = appointments.of(currentPatient.patientId()).stream()
                .filter(own -> own.id().equals(appointmentId))
                .findFirst()
                .orElseThrow(() -> new NotYourAppointmentException(appointmentId));
        WebCancellationPolicy.check(appointment, LocalDateTime.now(clock), hoursBefore);
        cancellations.cancelOnWeb(appointmentId);
    }
}
