package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import com.clinic.schedule.clinicalrecord.application.port.AttendancePort;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.domain.ClinicalRecordAccessPolicy;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * Reads what the access rule needs — when the practitioner last saw the patient, and whether they
 * are seeing them today — and asks {@link ClinicalRecordAccessPolicy}. Every use case of the
 * record asks the same, so it is read in one place, as {@code AgendaReader} does in the
 * appointment module. Package-private: a detail of the use cases, which own the transaction.
 *
 * <p>Having seen the patient means having recorded a consultation of them or having done a check
 * to them (D-53). Reporting a result does not count: whoever analyses a sample has not seen the
 * patient.
 */
class ClinicalRecordAccessReader {

    private final ConsultationRepository consultations;
    private final CheckRepository checks;
    private final AttendancePort attendance;
    private final ClinicalRecordAccessPolicy policy;

    ClinicalRecordAccessReader(ConsultationRepository consultations, CheckRepository checks, AttendancePort attendance,
            ClinicalRecordAccessPolicy policy) {
        this.consultations = consultations;
        this.checks = checks;
        this.attendance = attendance;
        this.policy = policy;
    }

    VisitToday visitOn(PractitionerId practitionerId, PatientId patientId, LocalDate today) {
        return attendance.visitOn(practitionerId, patientId, today);
    }

    boolean mayRead(PractitionerId practitionerId, PatientId patientId, VisitToday visit, LocalDate today) {
        return policy.mayRead(lastSeen(practitionerId, patientId), visit, today);
    }

    boolean mayRead(PractitionerId practitionerId, PatientId patientId, LocalDate today) {
        return mayRead(practitionerId, patientId, visitOn(practitionerId, patientId, today), today);
    }

    boolean mayRecord(VisitToday visit) {
        return policy.mayRecord(visit);
    }

    private Optional<LocalDateTime> lastSeen(PractitionerId practitionerId, PatientId patientId) {
        return Stream.of(
                        consultations.findLastRecordedAt(patientId, practitionerId),
                        checks.findLastPerformedAt(patientId, practitionerId))
                .flatMap(Optional::stream)
                .max(LocalDateTime::compareTo);
    }
}
