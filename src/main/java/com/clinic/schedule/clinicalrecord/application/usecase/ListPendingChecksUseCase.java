package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.port.CheckRepository;
import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * Every check of the practice still waiting for its result, the oldest first, for whoever reports
 * them (D-54). It shows only which patient and which check — no other part of the record — and,
 * for each one, whether the user may already read the record or has to ask for access.
 */
@Service
public class ListPendingChecksUseCase {

    private final CheckRepository checks;
    private final CurrentPractitionerPort currentPractitioner;
    private final ClinicalRecordAccessReader access;
    private final Clock clock;

    public ListPendingChecksUseCase(CheckRepository checks, CurrentPractitionerPort currentPractitioner,
            ClinicalRecordAccessReader access, Clock clock) {
        this.checks = checks;
        this.currentPractitioner = currentPractitioner;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PendingCheck> handle() {
        PractitionerId practitionerId = currentPractitioner.practitionerId()
                .orElseThrow(ClinicalRecordAccessDeniedException::toRead);
        LocalDate today = LocalDate.now(clock);
        return checks.findAllPending().stream()
                .sorted(Comparator.comparing(Check::performedAt))
                .map(check -> new PendingCheck(check, access.mayRead(practitionerId, check.patientId(), today)))
                .toList();
    }

    /** A pending check, and whether the user may already read its patient's record. */
    public record PendingCheck(Check check, boolean mayRead) {
    }
}
