package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.application.port.RecordCopyRepository;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;

/**
 * The copies of records asked for (D-95): those waiting for the practitioners, the soonest due first, each saying
 * whether the practitioner asking may read that record — to review it, or to ask for access first; the envelopes
 * waiting at reception; and a patient's own requests, for the portal. None of them is a reading: they show who
 * and when, never the record.
 */
@Service
public class ListRecordCopiesUseCase {

    private final RecordCopyRepository copies;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public ListRecordCopiesUseCase(RecordCopyRepository copies, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.copies = copies;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    /** @throws ClinicalRecordAccessDeniedException when the user is not a practitioner */
    @Transactional(readOnly = true)
    public List<Waiting> waiting() {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        LocalDate today = LocalDate.now(clock);
        return copies.findWaiting().stream()
                .sorted(Comparator.comparing(RecordCopy::deadline))
                .map(copy -> new Waiting(copy, access.mayRead(practitionerId, copy.patientId(), today)))
                .toList();
    }

    /** The envelopes on paper at reception, the oldest authenticated first. Reception's, by route. */
    @Transactional(readOnly = true)
    public List<RecordCopy> awaitingHandover() {
        return copies.findAwaitingHandover().stream()
                .sorted(Comparator.comparing(copy -> copy.authentication().orElseThrow().at()))
                .toList();
    }

    /** Every request of these patients' records, the most recent first. Whose they may be is the caller's. */
    @Transactional(readOnly = true)
    public List<RecordCopy> forPatients(Collection<PatientId> patients) {
        if (patients.isEmpty()) {
            return List.of();
        }
        return copies.findForPatients(patients).stream()
                .sorted(Comparator.comparing(RecordCopy::requestedAt).reversed())
                .toList();
    }

    /** A copy waiting, and whether the practitioner may read its record. */
    public record Waiting(RecordCopy copy, boolean mayRead) {
    }
}
