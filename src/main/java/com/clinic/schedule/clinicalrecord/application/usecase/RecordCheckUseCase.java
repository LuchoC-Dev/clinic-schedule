package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AttendancePort;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.ClinicalRecordAccessPolicy;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * A check done without a consultation: the precaution for an emergency, when the patient is
 * checked before anyone writes a consultation (D-53). Checks done during a consultation are sent
 * with it, by {@link RecordConsultationUseCase}.
 *
 * <p>The patient is there to be checked, so the rule is the consultation's: they arrived to be
 * seen by this practitioner today (D-49). The result may come with it or later.
 *
 * <p>Retrying is safe: an id already recorded by this practitioner answers that check (D-19).
 */
@Service
public class RecordCheckUseCase {

    private final CheckRepository checks;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public RecordCheckUseCase(CheckRepository checks, ConsultationRepository consultations,
            AttendancePort attendance, CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessPolicy policy,
            Clock clock) {
        this.checks = checks;
        this.currentPractitioner = currentPractitioner;
        this.access = new ClinicalRecordAccessReader(consultations, checks, attendance, policy);
        this.clock = clock;
    }

    @Transactional
    public Check handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);

        Optional<Check> alreadyRecorded = checks.findById(command.id());
        if (alreadyRecorded.isPresent()) {
            if (!alreadyRecorded.get().performedBy().equals(practitionerId)) {
                throw ClinicalRecordAccessDeniedException.toRead();
            }
            return alreadyRecorded.get();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        VisitToday visit = access.visitOn(practitionerId, command.patientId(), now.toLocalDate());
        if (!access.mayRecord(visit)) {
            boolean mayRead = access.mayRead(practitionerId, command.patientId(), visit, now.toLocalDate());
            throw mayRead ? ClinicalRecordAccessDeniedException.toRecord() : ClinicalRecordAccessDeniedException.toRead();
        }

        Check check = command.result() == null || command.result().isBlank()
                ? Check.perform(command.id(), command.patientId(), practitionerId, now, command.description(), null)
                : Check.performWithResult(command.id(), command.patientId(), practitionerId, now,
                        command.description(), null, command.result());
        checks.save(check);
        return check;
    }

    /** @param result null or blank while it is pending */
    public record Command(CheckId id, PatientId patientId, String description, String result) {
    }
}
