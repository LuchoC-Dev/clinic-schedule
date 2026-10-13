package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.CheckNotFoundException;
import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyNotFoundException;
import com.clinic.schedule.clinicalrecord.application.port.AccessGrantRepository;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.application.port.RecordCopyRepository;
import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.AccessGrantId;
import com.clinic.schedule.clinicalrecord.domain.AccessGrantPolicy;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;

/**
 * A practitioner asks for access to a patient's clinical record to report one of the patient's
 * pending checks (D-54), or to authenticate a copy of it the patient asked for (D-95). Granted at once, for now, and recorded: who, for which patient and check,
 * when, until when, why, and how it was approved.
 *
 * <p>Only for a pending check: with an automatic approval, that is what keeps a grant from opening
 * any record at will. A practitioner who can already read the record may ask anyway; it does no
 * harm, and it is recorded like any other.
 */
@Service
public class RequestAccessUseCase {

    private final CheckRepository checks;
    private final RecordCopyRepository copies;
    private final AccessGrantRepository grants;
    private final CurrentPractitionerPort currentPractitioner;
    private final AccessGrantPolicy policy;
    private final Clock clock;

    public RequestAccessUseCase(CheckRepository checks, RecordCopyRepository copies, AccessGrantRepository grants,
            CurrentPractitionerPort currentPractitioner, AccessGrantPolicy policy, Clock clock) {
        this.checks = checks;
        this.copies = copies;
        this.grants = grants;
        this.currentPractitioner = currentPractitioner;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * @throws CheckNotFoundException when there is no such check
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.CheckNotPendingException when it
     *         already has its result
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.ReasonRequiredException when a
     *         reason is required and none was given
     */
    @Transactional
    public AccessGrant handle(Command command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        Check check = checks.findById(command.checkId())
                .orElseThrow(() -> new CheckNotFoundException(command.checkId()));

        AccessGrant grant = policy.grant(AccessGrantId.newId(), practitionerId, check, LocalDateTime.now(clock),
                command.reason());
        grants.save(grant);
        return grant;
    }

    /**
     * Access to authenticate a copy of the record the patient asked for, while it waits (D-95): granted at once and
     * recorded, as for a check.
     *
     * @throws RecordCopyNotFoundException when there is no such copy
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException when it is
     *         authenticated already
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.ReasonRequiredException when a reason is
     *         required and none was given
     */
    @Transactional
    public AccessGrant forCopy(CopyCommand command) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        RecordCopy copy = copies.findById(command.recordCopyId()).orElseThrow(RecordCopyNotFoundException::new);

        AccessGrant grant = policy.grantForCopy(AccessGrantId.newId(), practitionerId, copy, LocalDateTime.now(clock),
                command.reason());
        grants.save(grant);
        return grant;
    }

    /** @param reason null or blank when none is given */
    public record Command(CheckId checkId, String reason) {
    }

    /** @param reason null or blank when none is given */
    public record CopyCommand(RecordCopyId recordCopyId, String reason) {
    }
}
