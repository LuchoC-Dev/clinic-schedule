package com.clinic.schedule.portal.application.port;

import java.util.List;
import java.util.UUID;

import com.clinic.schedule.portal.domain.Appointment;

/** A patient's appointments, as the appointment module answers them: every one, in no order. */
public interface PatientAppointmentsPort {

    List<Appointment> of(UUID patientId);
}
