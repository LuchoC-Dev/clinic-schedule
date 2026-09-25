package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AttendancePort;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.ClinicalRecordAccessPolicy;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * A patient's clinical record: their consultations, from every practitioner of the practice,
 * the most recent first.
 *
 * <p>Whether the user may read it is decided here, not by route (D-34): the route only says it
 * takes a practitioner. Which practitioner may read which patient is the rule of
 * {@link ClinicalRecordAccessPolicy}, and a refusal is a 403 — for a patient who does not exist
 * as well, so the answer never tells whether they do (D-49, D-50).
 */
@Service
public class ViewClinicalRecordUseCase {

    private final ConsultationRepository consultations;
    private final AttendancePort attendance;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessPolicy policy;
    private final Clock clock;

    public ViewClinicalRecordUseCase(ConsultationRepository consultations, AttendancePort attendance,
            CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessPolicy policy, Clock clock) {
        this.consultations = consultations;
        this.attendance = attendance;
        this.currentPractitioner = currentPractitioner;
        this.policy = policy;
        this.clock = clock;
    }

    /** @throws ClinicalRecordAccessDeniedException when the user may not read this record */
    @Transactional(readOnly = true)
    public ClinicalRecord handle(PatientId patientId) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        LocalDate today = LocalDate.now(clock);
        VisitToday visit = attendance.visitOn(practitionerId, patientId, today);
        if (!policy.mayRead(consultations.findLastRecordedAt(patientId, practitionerId), visit, today)) {
            throw ClinicalRecordAccessDeniedException.toRead();
        }
        List<Consultation> found = consultations.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(Consultation::recordedAt).reversed())
                .toList();
        return new ClinicalRecord(found, policy.mayRecord(visit));
    }

    /**
     * The record, and whether the user may add a consultation to it right now — so a screen
     * offers the form only when sending it would be accepted.
     */
    public record ClinicalRecord(List<Consultation> consultations, boolean mayRecord) {
    }
}
