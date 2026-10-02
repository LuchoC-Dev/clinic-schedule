package com.clinic.schedule.portal.adapter.out.external;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.PatientAppointment;
import com.clinic.schedule.portal.application.port.PatientAppointmentsPort;
import com.clinic.schedule.portal.domain.Appointment;

/** Answers {@link PatientAppointmentsPort} by asking the appointment module through its api (D-05). */
@Component
class PatientAppointmentsAdapter implements PatientAppointmentsPort {

    private final AppointmentReader appointments;

    PatientAppointmentsAdapter(AppointmentReader appointments) {
        this.appointments = appointments;
    }

    @Override
    public List<Appointment> of(UUID patientId) {
        return appointments.findForPatient(patientId).stream()
                .map(PatientAppointmentsAdapter::appointment)
                .toList();
    }

    private static Appointment appointment(PatientAppointment appointment) {
        return new Appointment(appointment.id(), appointment.practitionerId(), appointment.startsAt(),
                appointment.reason(), Appointment.State.valueOf(appointment.state().name()),
                appointment.cancellationRequestedBy() == null ? null
                        : Appointment.Requester.valueOf(appointment.cancellationRequestedBy().name()));
    }
}
