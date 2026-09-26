package com.clinic.schedule.clinicalrecord.application.port;

import java.time.LocalDate;

import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * Whether a practitioner is seeing a patient on a day. A {@code Port}: it is another module,
 * not storage (D-18).
 *
 * <p>It is answered by the appointments and by the arrival queues, and only the adapter knows
 * (D-48, D-63).
 */
public interface AttendancePort {

    VisitToday visitOn(PractitionerId practitionerId, PatientId patientId, LocalDate day);
}
