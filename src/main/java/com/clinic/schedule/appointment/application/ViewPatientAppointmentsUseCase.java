package com.clinic.schedule.appointment.application;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PatientId;

/**
 * Every appointment of a patient, cancelled ones included, the most recent first: what they
 * booked and what became of it.
 *
 * <p>This is only the list of appointments, not the patient file of the planned evolution,
 * which will hold much more (D-35). Only reception reaches it, and that is decided by route:
 * there is no "whose" question, since reception may read every patient (D-34).
 */
@Service
public class ViewPatientAppointmentsUseCase {

    private final AppointmentRepository appointments;

    public ViewPatientAppointmentsUseCase(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public List<Appointment> handle(PatientId patientId) {
        return appointments.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing((Appointment appointment) -> appointment.slot().startsAt()).reversed())
                .toList();
    }
}
