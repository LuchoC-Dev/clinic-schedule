package com.clinic.schedule.consultation.application.port;

import java.time.LocalDate;

import com.clinic.schedule.consultation.domain.PatientId;
import com.clinic.schedule.consultation.domain.PractitionerId;
import com.clinic.schedule.consultation.domain.VisitToday;

/**
 * Whether a practitioner is seeing a patient on a day. A {@code Port}: it is another module,
 * not storage (D-18).
 *
 * <p>Today it is answered by the appointments and their check-in; once the arrival queue
 * exists, by the queue, and only the adapter changes (D-48).
 */
public interface AttendancePort {

    VisitToday visitOn(PractitionerId practitionerId, PatientId patientId, LocalDate day);
}
