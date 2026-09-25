package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.clinic.schedule.clinicalrecord.application.usecase.ViewClinicalRecordUseCase;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;

/**
 * One access granted to a clinical record, as the record screen shows it: to whom, when, until
 * when, for which check, and why (D-54).
 */
record GrantEntry(String practitioner, String grantedAt, String validUntil, String check, String reason) {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** @param names every practitioner's name by id */
    static List<GrantEntry> of(ViewClinicalRecordUseCase.ClinicalRecord record, Map<UUID, String> names) {
        Map<CheckId, Check> checks = record.checks().stream()
                .collect(Collectors.toMap(Check::id, Function.identity()));
        return record.grants().stream()
                .map(grant -> new GrantEntry(
                        ClinicalRecordEntry.nameOf(names, grant.practitionerId().value()),
                        ClinicalRecordEntry.format(grant.grantedAt()),
                        grant.validUntil().format(DAY),
                        checks.containsKey(grant.checkId()) ? checks.get(grant.checkId()).description() : "",
                        grant.reason().orElse("")))
                .toList();
    }
}
