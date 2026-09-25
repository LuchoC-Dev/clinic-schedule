package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.ConsultationChangedException;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.ConsultationRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Addendum;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.AddendumId;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * A practitioner adds an addendum to a consultation already sent, with the practice's clock
 * and themselves as the author (D-52).
 *
 * <p>Any practitioner who may read the patient's clinical record may add one, at any time; the
 * rule is the record's (D-49). A consultation that does not exist is refused like one the user
 * may not read, so the answer never tells whether it does.
 *
 * <p>Retrying is safe: an addendum id the consultation already has answers the consultation as
 * it is (D-19). Two addenda at the same time do not overwrite each other: the second one is
 * refused with {@link ConsultationChangedException} and can be sent again.
 */
@Service
public class AddAddendumUseCase {

    private final ConsultationRepository consultations;
    private final CheckRepository checks;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public AddAddendumUseCase(ConsultationRepository consultations, CheckRepository checks,
            CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessReader access, Clock clock) {
        this.consultations = consultations;
        this.checks = checks;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    /**
     * @throws ClinicalRecordAccessDeniedException when the user may not read the patient's record
     * @throws ConsultationChangedException when another addendum was added at the same time
     */
    @Transactional
    public Added handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        Consultation consultation = consultations.findById(command.consultationId())
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);

        LocalDateTime now = LocalDateTime.now(clock);
        if (!access.mayRead(practitionerId, consultation.patientId(), now.toLocalDate())) {
            throw ClinicalRecordAccessDeniedException.toRead();
        }

        if (consultation.hasAddendum(command.addendumId())) {
            return new Added(consultation, checks.findAllForConsultation(consultation.id()));
        }
        Consultation added = consultation.withAddendum(
                new Addendum(command.addendumId(), practitionerId, now, command.text()));
        consultations.save(added);
        return new Added(added, checks.findAllForConsultation(added.id()));
    }

    /** The consultation with its new addendum, and the checks done during it, to answer it whole. */
    public record Added(Consultation consultation, List<Check> checks) {
    }

    public record Command(ConsultationId consultationId, AddendumId addendumId, String text) {
    }
}
