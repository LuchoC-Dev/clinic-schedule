package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.CheckChangedException;
import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Addendum;
import com.clinic.schedule.clinicalrecord.domain.AddendumId;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * A practitioner adds an addendum to a check — pending or reported — with the practice's clock and
 * themselves as the author: how a wrong result is corrected, since a result never changes (D-58).
 *
 * <p>The rule is the consultation's (D-52): any practitioner who may read the patient's record, at
 * any time. A check that does not exist is refused like one the user may not read. Retrying is
 * safe: an addendum id the check already has answers the check as it is. Two addenda at the same
 * time do not overwrite each other: the second is refused with {@link CheckChangedException}.
 */
@Service
public class AddCheckAddendumUseCase {

    private final CheckRepository checks;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public AddCheckAddendumUseCase(CheckRepository checks, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.checks = checks;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Check handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        Check check = checks.findById(command.checkId()).orElseThrow(ClinicalRecordAccessDeniedException::toRead);

        LocalDateTime now = LocalDateTime.now(clock);
        if (!access.mayRead(practitionerId, check.patientId(), now.toLocalDate())) {
            throw ClinicalRecordAccessDeniedException.toRead();
        }
        if (check.hasAddendum(command.addendumId())) {
            return check;
        }
        Check added = check.withAddendum(new Addendum(command.addendumId(), practitionerId, now, command.text()));
        checks.save(added);
        return added;
    }

    public record Command(CheckId checkId, AddendumId addendumId, String text) {
    }
}
