package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientAppointmentsPort;
import com.clinic.schedule.portal.domain.OwnAppointments;

/**
 * The appointments of the patient logged in, as {@link OwnAppointments} lists them (D-80). Whose they are is
 * never asked: it is whoever is logged in, so no patient can ask for another's.
 */
@Service
public class ListOwnAppointmentsUseCase {

    private final CurrentPatientPort currentPatient;
    private final PatientAppointmentsPort appointments;
    private final Clock clock;

    public ListOwnAppointmentsUseCase(CurrentPatientPort currentPatient, PatientAppointmentsPort appointments,
            Clock clock) {
        this.currentPatient = currentPatient;
        this.appointments = appointments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OwnAppointments handle() {
        return OwnAppointments.of(appointments.of(currentPatient.patientId()), LocalDateTime.now(clock));
    }
}
