package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.AttachmentRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckResult;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;

/**
 * The result of a pending check, recorded later — by whoever did the check or by someone else,
 * such as whoever analyses a blood sample (D-53). The patient need not be there: any practitioner
 * who may read the patient's clinical record may record it (D-49). A check that does not exist is
 * refused like one the user may not read.
 *
 * <p>A check has one result and it never changes: a second one, or one recorded at the same time
 * by someone else, is refused with {@link ResultAlreadyRecordedException}. Sending the same result
 * again by its author is taken as a retry and answers the check as it is.
 *
 * <p>The text may be left out when the check has an attachment that says it — a laboratory's PDF
 * (D-57).
 */
@Service
public class RecordCheckResultUseCase {

    private final CheckRepository checks;
    private final AttachmentRepository attachments;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public RecordCheckResultUseCase(CheckRepository checks, AttachmentRepository attachments,
            CurrentPractitionerPort currentPractitioner, ClinicalRecordAccessReader access, Clock clock) {
        this.checks = checks;
        this.attachments = attachments;
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

        CheckResult result = new CheckResult(practitionerId, now, command.text());
        boolean isARetry = check.result()
                .filter(existing -> existing.author().equals(practitionerId)
                        && Objects.equals(existing.text(), result.text()))
                .isPresent();
        if (isARetry) {
            return check;
        }
        Check reported = check.withResult(result, attachments.existsFor(new AttachmentOwner.OfCheck(check.id())));
        checks.save(reported);
        return reported;
    }

    public record Command(CheckId checkId, String text) {
    }
}
