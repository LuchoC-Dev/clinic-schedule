package com.clinic.schedule.clinicalrecord.application.usecase;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.port.RecordCopyRepository;
import com.clinic.schedule.clinicalrecord.domain.CopyCode;
import com.clinic.schedule.clinicalrecord.domain.CopyDelivery;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;
import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException;

/**
 * A copy of a patient's whole record is asked for, from a web account (D-95): put together as the record stands
 * now, with a code of its own, to be authenticated by a practitioner within 48 hours.
 *
 * <p>Who may ask for whose is the portal's to say — the patient from 13, or a parent with the relation proved —
 * and it asks only then; this module keeps the request. One copy waits at a time for each record, so the
 * practitioners are not asked twice for the same.
 */
@Service
public class RequestRecordCopyUseCase {

    private final RecordCopyRepository copies;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RequestRecordCopyUseCase(RecordCopyRepository copies, Clock clock) {
        this.copies = copies;
        this.clock = clock;
    }

    /** @throws RecordCopyRefusedException when the record already has a copy waiting */
    @Transactional
    public RecordCopy handle(Command command) {
        if (copies.existsWaitingFor(command.patientId())) {
            throw new RecordCopyRefusedException(RecordCopyRefusedException.Reason.ALREADY_WAITING);
        }
        RecordCopy copy = RecordCopy.request(RecordCopyId.newId(), command.patientId(), command.requestedBy(),
                command.requestedByAccount(), LocalDateTime.now(clock), command.delivery(), CopyCode.generate(random));
        copies.save(copy);
        return copy;
    }

    /**
     * @param requestedBy the patient who asked
     * @param requestedByAccount the name their account goes by (D-81)
     */
    public record Command(PatientId patientId, UUID requestedBy, String requestedByAccount, CopyDelivery delivery) {
    }
}
