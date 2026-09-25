package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationItem;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.VisitToday;

/**
 * A practitioner sends the consultation of a patient they are seeing today, with the checks done
 * during it. It is recorded whole, once, with the practice's clock; the consultation never changes
 * afterwards, and each check only gains its result if it was pending (D-47, D-53).
 *
 * <p>The consultation and its checks are stored in one transaction: all of them or none.
 *
 * <p>The practitioner is whoever is using the application, never something the caller names.
 * The patient must have arrived to be seen by them today (D-49); otherwise the refusal is a 403,
 * and it says whether the practitioner could at least read the record, so a screen can tell
 * "not arrived yet" from "not yours".
 *
 * <p>Retrying is safe: an id already recorded by this practitioner answers that consultation and
 * its checks instead of recording them twice (D-19). An id recorded by someone else is refused,
 * rather than answered with a consultation the user might not be allowed to read.
 */
@Service
public class RecordConsultationUseCase {

    private final ConsultationRepository consultations;
    private final CheckRepository checks;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public RecordConsultationUseCase(ConsultationRepository consultations, CheckRepository checks,
            CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessReader access, Clock clock) {
        this.consultations = consultations;
        this.checks = checks;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Recorded handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);

        Optional<Consultation> alreadyRecorded = consultations.findById(command.id());
        if (alreadyRecorded.isPresent()) {
            if (!alreadyRecorded.get().practitionerId().equals(practitionerId)) {
                throw ClinicalRecordAccessDeniedException.toRead();
            }
            return new Recorded(alreadyRecorded.get(), checksOf(alreadyRecorded.get()));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        VisitToday visit = access.visitOn(practitionerId, command.patientId(), now.toLocalDate());
        if (!access.mayRecord(visit)) {
            boolean mayRead = access.mayRead(practitionerId, command.patientId(), visit, now.toLocalDate());
            throw mayRead ? ClinicalRecordAccessDeniedException.toRecord() : ClinicalRecordAccessDeniedException.toRead();
        }

        Consultation consultation = Consultation.record(command.id(), command.patientId(), practitionerId, now,
                command.notes(), command.items());
        List<Check> done = command.checks().stream()
                .map(check -> check.toCheck(command.patientId(), practitionerId, now, command.id()))
                .toList();
        consultations.save(consultation);
        done.forEach(checks::save);
        return new Recorded(consultation, done);
    }

    private List<Check> checksOf(Consultation consultation) {
        return checks.findAllForConsultation(consultation.id());
    }

    /**
     * What the practitioner writes. Who writes it and when are not asked: they are known.
     *
     * @param checks the checks done during the consultation, in the order they were written
     */
    public record Command(ConsultationId id, PatientId patientId, String notes, List<ConsultationItem> items,
            List<CheckDone> checks) {

        public Command {
            checks = checks == null ? List.of() : List.copyOf(checks);
            if (checks.size() > Consultation.MAX_ITEMS) {
                throw new IllegalArgumentException("A consultation has at most " + Consultation.MAX_ITEMS + " checks");
            }
        }
    }

    /** A check done during the consultation: what was done, and its result when it is known at once. */
    public record CheckDone(String description, String result) {

        Check toCheck(PatientId patientId, PractitionerId practitionerId, LocalDateTime at, ConsultationId consultationId) {
            return result == null || result.isBlank()
                    ? Check.perform(CheckId.newId(), patientId, practitionerId, at, description, consultationId)
                    : Check.performWithResult(CheckId.newId(), patientId, practitionerId, at, description,
                            consultationId, result);
        }
    }

    /** The consultation recorded, and the checks done during it. */
    public record Recorded(Consultation consultation, List<Check> checks) {
    }
}
