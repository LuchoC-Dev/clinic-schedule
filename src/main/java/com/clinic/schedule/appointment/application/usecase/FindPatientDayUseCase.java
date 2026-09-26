package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PatientId;

/**
 * A patient's appointments on a day, with any practitioner, booked or absent — every one not
 * called off. What the clinical record module asks to know whether a practitioner is seeing the
 * patient today (D-48, D-61).
 */
@Service
public class FindPatientDayUseCase {

    private final AppointmentRepository appointments;

    public FindPatientDayUseCase(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public List<Appointment> handle(PatientId patientId, LocalDate date) {
        return appointments.findNotCancelledForPatientOn(patientId, date);
    }
}
