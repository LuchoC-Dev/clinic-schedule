package com.clinic.schedule.clinicalrecord.adapter.out.external;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.AppointmentSummary;
import com.clinic.schedule.arrival.api.ArrivalReader;
import com.clinic.schedule.arrival.api.QueueArrival;
import com.clinic.schedule.clinicalrecord.application.port.AttendancePort;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * Answers {@link AttendancePort} from the appointments and the queues, through their modules'
 * apis (D-48, D-63). The only class that knows where the answer comes from.
 *
 * <ul>
 *   <li><b>Arrived</b> — the record may be written — when the practitioner called the patient from
 *       a queue that day, or saw them, or when the patient checked in on an appointment with them.
 *       Whoever was called and did not come in is not theirs.</li>
 *   <li><b>Expected</b> — the record may be read — when the patient waits in a queue the
 *       practitioner serves, or has an appointment with them that day, even an absent one (D-61).</li>
 * </ul>
 *
 * <p>The check-in on the appointment goes when the queue takes the arrival over, in part 5b.
 */
@Component
class AttendanceAdapter implements AttendancePort {

    private final AppointmentReader appointments;
    private final ArrivalReader arrivals;

    AttendanceAdapter(AppointmentReader appointments, ArrivalReader arrivals) {
        this.appointments = appointments;
        this.arrivals = arrivals;
    }

    @Override
    public VisitToday visitOn(PractitionerId practitionerId, PatientId patientId, LocalDate day) {
        List<AppointmentSummary> withThisPractitioner = appointments.findNotCancelledForPatientOn(patientId.value(), day)
                .stream()
                .filter(appointment -> appointment.practitionerId().equals(practitionerId.value()))
                .toList();
        List<QueueArrival> inQueues = arrivals.findForPatientOn(patientId.value(), day);
        if (withThisPractitioner.stream().anyMatch(AppointmentSummary::checkedIn)
                || inQueues.stream().anyMatch(arrival -> practitionerId.value().equals(arrival.withPractitioner()))) {
            return VisitToday.ARRIVED;
        }
        if (!withThisPractitioner.isEmpty() || inQueues.stream()
                .anyMatch(arrival -> arrival.waiting() && arrival.servedBy().contains(practitionerId.value()))) {
            return VisitToday.EXPECTED;
        }
        return VisitToday.NONE;
    }
}
