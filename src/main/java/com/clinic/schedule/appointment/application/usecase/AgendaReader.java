package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * Gathers the appointments that could collide with a slot: those of the practitioner, and
 * those of the patient with anyone.
 *
 * <p>Both booking and rescheduling need exactly this, which is why it is not repeated in
 * each use case. It normally reads one day; it reads two when a slot crosses midnight, which
 * should not happen in a practice but costs nothing to handle.
 */
class AgendaReader {

    private final AppointmentRepository appointments;

    AgendaReader(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    List<Appointment> around(TimeSlot slot, PractitionerId practitionerId, PatientId patientId) {
        List<Appointment> agenda = new ArrayList<>();
        for (LocalDate date : datesTouchedBy(slot)) {
            agenda.addAll(appointments.findBookedForPractitionerOn(practitionerId, date));
            agenda.addAll(appointments.findBookedForPatientOn(patientId, date));
        }
        return agenda;
    }

    private Set<LocalDate> datesTouchedBy(TimeSlot slot) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        dates.add(slot.startsAt().toLocalDate());
        dates.add(slot.endsAt().toLocalDate());
        return dates;
    }
}
