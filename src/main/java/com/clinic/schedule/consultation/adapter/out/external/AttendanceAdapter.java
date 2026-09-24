package com.clinic.schedule.consultation.adapter.out.external;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.AppointmentSummary;
import com.clinic.schedule.consultation.application.port.AttendancePort;
import com.clinic.schedule.consultation.domain.PatientId;
import com.clinic.schedule.consultation.domain.PractitionerId;
import com.clinic.schedule.consultation.domain.VisitToday;

/**
 * Answers {@link AttendancePort} from the appointments, through the appointment module's api:
 * a booked appointment with the practitioner that day means the patient is expected, and one
 * with a check-in that they arrived.
 *
 * <p>The only class that knows the arrival is an appointment's check-in today. When the arrival
 * queue takes the arrival over, this is what changes (D-48).
 */
@Component
class AttendanceAdapter implements AttendancePort {

    private final AppointmentReader appointments;

    AttendanceAdapter(AppointmentReader appointments) {
        this.appointments = appointments;
    }

    @Override
    public VisitToday visitOn(PractitionerId practitionerId, PatientId patientId, LocalDate day) {
        List<AppointmentSummary> withThisPractitioner = appointments.findBookedForPatientOn(patientId.value(), day)
                .stream()
                .filter(appointment -> appointment.practitionerId().equals(practitionerId.value()))
                .toList();
        if (withThisPractitioner.stream().anyMatch(AppointmentSummary::checkedIn)) {
            return VisitToday.ARRIVED;
        }
        return withThisPractitioner.isEmpty() ? VisitToday.NONE : VisitToday.EXPECTED;
    }
}
