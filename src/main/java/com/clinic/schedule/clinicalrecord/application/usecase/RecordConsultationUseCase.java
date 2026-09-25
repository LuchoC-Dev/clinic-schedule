package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AttendancePort;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.ClinicalRecordAccessPolicy;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationItem;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * A practitioner sends the consultation of a patient they are seeing today. It is recorded
 * whole, once, with the practice's clock, and never changes afterwards (D-47).
 *
 * <p>The practitioner is whoever is using the application, never something the caller names.
 * The patient must have arrived to be seen by them today (D-49); otherwise the refusal is a 403,
 * and it says whether the practitioner could at least read the record, so a screen can tell
 * "not arrived yet" from "not yours".
 *
 * <p>Retrying is safe: an id already recorded by this practitioner answers that consultation
 * instead of recording a second one (D-19). An id recorded by someone else is refused, rather
 * than answered with a consultation the user might not be allowed to read.
 */
@Service
public class RecordConsultationUseCase {

    private final ConsultationRepository consultations;
    private final AttendancePort attendance;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessPolicy policy;
    private final Clock clock;

    public RecordConsultationUseCase(ConsultationRepository consultations, AttendancePort attendance,
            CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessPolicy policy, Clock clock) {
        this.consultations = consultations;
        this.attendance = attendance;
        this.currentPractitioner = currentPractitioner;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public Consultation handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);

        Optional<Consultation> alreadyRecorded = consultations.findById(command.id());
        if (alreadyRecorded.isPresent()) {
            if (!alreadyRecorded.get().practitionerId().equals(practitionerId)) {
                throw ClinicalRecordAccessDeniedException.toRead();
            }
            return alreadyRecorded.get();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        VisitToday visit = attendance.visitOn(practitionerId, command.patientId(), now.toLocalDate());
        if (!policy.mayRecord(visit)) {
            boolean mayRead = policy.mayRead(
                    consultations.findLastRecordedAt(command.patientId(), practitionerId), visit, now.toLocalDate());
            throw mayRead ? ClinicalRecordAccessDeniedException.toRecord() : ClinicalRecordAccessDeniedException.toRead();
        }

        Consultation consultation = Consultation.record(command.id(), command.patientId(), practitionerId, now,
                command.notes(), command.items());
        consultations.save(consultation);
        return consultation;
    }

    /** What the practitioner writes. Who writes it and when are not asked: they are known. */
    public record Command(ConsultationId id, PatientId patientId, String notes, List<ConsultationItem> items) {
    }
}
