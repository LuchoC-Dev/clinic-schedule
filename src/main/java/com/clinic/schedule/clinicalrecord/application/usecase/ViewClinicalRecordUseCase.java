package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AccessGrantRepository;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckOrderRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.application.port.RecordReadingWriter;
import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.ClinicalRecordAccessPolicy;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.ReadAccess;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * A patient's clinical record: their consultations and their checks, from every practitioner of
 * the practice, and the access granted to it, the most recent first (D-53, D-54).
 *
 * <p>Whether the user may read it is decided here, not by route (D-34): the route only says it
 * takes a practitioner. Which practitioner may read which patient is the rule of
 * {@link ClinicalRecordAccessPolicy}, and a refusal is a 403 — for a patient who does not exist
 * as well, so the answer never tells whether they do (D-49, D-50).
 *
 * <p>Every reading is recorded, with the access that allowed it, in the same transaction: a record
 * that cannot be traced is not read (D-59). So this read writes.
 */
@Service
public class ViewClinicalRecordUseCase {

    private final ConsultationRepository consultations;
    private final CheckRepository checks;
    private final AccessGrantRepository grants;
    private final CheckOrderRepository orders;
    private final AttachmentRepository attachments;
    private final RecordReadingWriter readings;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public ViewClinicalRecordUseCase(ConsultationRepository consultations, CheckRepository checks,
            AccessGrantRepository grants, CheckOrderRepository orders, AttachmentRepository attachments,
            RecordReadingWriter readings, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.consultations = consultations;
        this.checks = checks;
        this.grants = grants;
        this.orders = orders;
        this.attachments = attachments;
        this.readings = readings;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    /** @throws ClinicalRecordAccessDeniedException when the user may not read this record */
    @Transactional
    public ClinicalRecord handle(PatientId patientId) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        VisitToday visit = access.visitOn(practitionerId, patientId, today);
        ReadAccess readAccess = access.readAccess(practitionerId, patientId, visit, today)
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        readings.save(RecordReading.ofRecord(practitionerId, currentPractitioner.username(), patientId, now, readAccess));
        List<Consultation> foundConsultations = consultations.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(Consultation::recordedAt).reversed())
                .toList();
        List<Check> foundChecks = checks.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(Check::performedAt).reversed())
                .toList();
        List<AccessGrant> foundGrants = grants.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(AccessGrant::grantedAt).reversed())
                .toList();
        List<CheckOrder> foundOrders = orders.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(CheckOrder::orderedAt))
                .toList();
        List<Attachment> foundAttachments = attachments.findAllForPatient(patientId).stream()
                .sorted(Comparator.comparing(Attachment::uploadedAt))
                .toList();
        return new ClinicalRecord(foundConsultations, foundChecks, foundOrders, foundAttachments, foundGrants,
                practitionerId, access.mayRecord(visit));
    }

    /**
     * The record, and whether the user may add a consultation or a check to it right now — so a
     * screen offers the forms only when sending them would be accepted.
     *
     * @param checks every check, done in a consultation or not; each says which
     * @param orders every order for the patient, open or not, each with the consultation it was issued in (D-55)
     * @param attachments every file attached to a consultation or a check, each saying which (D-57)
     * @param grants every access granted to the record, to whoever, valid or not: who else opened it (D-54)
     * @param reader the practitioner reading it, so a screen can offer what only they may do
     */
    public record ClinicalRecord(List<Consultation> consultations, List<Check> checks, List<CheckOrder> orders,
            List<Attachment> attachments, List<AccessGrant> grants, PractitionerId reader, boolean mayRecord) {
    }
}
