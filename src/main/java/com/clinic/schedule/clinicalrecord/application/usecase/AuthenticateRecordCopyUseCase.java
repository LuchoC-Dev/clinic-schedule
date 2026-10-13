package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyNotFoundException;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.application.port.RecordCopyRepository;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;

/**
 * A practitioner who may read the record finishes a copy of it (D-95): puts it together again once they uploaded
 * what was missing (D-94), and authenticates it — their login is their electronic signature (Ley 25.506, art. 5;
 * Decreto 1089/2012, art. 13). Who may read the record is D-49's rule, at the moment of doing it; one who may not
 * is refused as for reading it, since doing either is vouching for what it holds.
 */
@Service
public class AuthenticateRecordCopyUseCase {

    private final RecordCopyRepository copies;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public AuthenticateRecordCopyUseCase(RecordCopyRepository copies, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.copies = copies;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    /**
     * @throws RecordCopyNotFoundException when there is no such copy
     * @throws ClinicalRecordAccessDeniedException when the user may not read that record
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException once authenticated
     */
    @Transactional
    public RecordCopy reassemble(RecordCopyId id) {
        LocalDateTime now = LocalDateTime.now(clock);
        RecordCopy copy = readable(id, now);
        RecordCopy again = copy.reassembled(now);
        copies.save(again);
        return again;
    }

    /**
     * @param assembly the assembly the practitioner reviewed
     * @param reviewed whether they said they reviewed that it is complete
     * @throws RecordCopyNotFoundException when there is no such copy
     * @throws ClinicalRecordAccessDeniedException when the user may not read that record
     * @throws com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException when it was
     *         authenticated already, put together again since, or not reviewed
     */
    @Transactional
    public RecordCopy authenticate(RecordCopyId id, int assembly, boolean reviewed) {
        LocalDateTime now = LocalDateTime.now(clock);
        RecordCopy copy = readable(id, now);
        PractitionerId practitionerId = currentPractitioner.practitionerId().orElseThrow();
        RecordCopy authenticated = copy.authenticated(practitionerId, currentPractitioner.username(), now, assembly,
                reviewed);
        copies.save(authenticated);
        return authenticated;
    }

    private RecordCopy readable(RecordCopyId id, LocalDateTime now) {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        RecordCopy copy = copies.findById(id).orElseThrow(RecordCopyNotFoundException::new);
        if (!access.mayRead(practitionerId, copy.patientId(), now.toLocalDate())) {
            throw ClinicalRecordAccessDeniedException.toRead();
        }
        return copy;
    }
}
