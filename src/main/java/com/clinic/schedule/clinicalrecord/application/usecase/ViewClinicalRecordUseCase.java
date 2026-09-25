package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AccessGrantRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.ClinicalRecordAccessPolicy;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * A patient's clinical record: their consultations and their checks, from every practitioner of
 * the practice, and the access granted to it, the most recent first (D-53, D-54).
 *
 * <p>Whether the user may read it is decided here, not by route (D-34): the route only says it
 * takes a practitioner. Which practitioner may read which patient is the rule of
 * {@link ClinicalRecordAccessPolicy}, and a refusal is a 403 — for a patient who does not exist
 * as well, so the answer never tells whether they do (D-49, D-50).
 */
@Service
public class ViewClinicalRecordUseCase {

    private final ConsultationRepository consultations;
    private final CheckRepository checks;
    private final AccessGrantRepository grants;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public ViewClinicalRecordUseCase(ConsultationRepository consultations, CheckRepository checks,
            AccessGrantRepository grants, CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessReader access,
            Clock clock) {
        this.consultations = consultations;
        this.checks = checks;
        this.grants = grants;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    /** @throws ClinicalRecordAccessDeniedException when the user may not read this record */
    @Transactional(readOnly = true)
    public ClinicalRecord handle(PatientId patientId) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        LocalDate today = LocalDate.now(clock);
        VisitToday visit = access.visitOn(practitionerId, patientId, today);
        if (!access.mayRead(practitionerId, patientId, visit, today)) {
            throw ClinicalRecordAccessDeniedException.toRead();
        }
        List<Consultation> foundConsultations = consultations.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(Consultation::recordedAt).reversed())
                .toList();
        List<Check> foundChecks = checks.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(Check::performedAt).reversed())
                .toList();
        List<AccessGrant> foundGrants = grants.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(AccessGrant::grantedAt).reversed())
                .toList();
        return new ClinicalRecord(foundConsultations, foundChecks, foundGrants, access.mayRecord(visit));
    }

    /**
     * The record, and whether the user may add a consultation or a check to it right now — so a
     * screen offers the forms only when sending them would be accepted.
     *
     * @param checks every check, done in a consultation or not; each says which
     * @param grants every access granted to the record, to whoever, valid or not: who else opened it (D-54)
     */
    public record ClinicalRecord(List<Consultation> consultations, List<Check> checks, List<AccessGrant> grants,
            boolean mayRecord) {
    }
}
