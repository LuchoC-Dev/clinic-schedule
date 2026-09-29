package com.clinic.schedule.clinicalrecord.adapter.in.event;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.clinic.schedule.clinicalrecord.application.usecase.RecordRouteRefusalUseCase;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.security.api.ClinicalRouteRefused;

/**
 * Hears from the security module that a clinical route was refused to someone's role, and records it
 * (D-72). An event, since this module already depends on the security module's api and the other way
 * round would close a cycle.
 *
 * <p>When the route names a patient — their clinical record —, the refusal says whose it was.
 */
@Component
class ClinicalRouteRefusedListener {

    private static final Pattern RECORD = Pattern.compile("^(?:/api)?/clinical-records/([0-9a-fA-F-]{36})(?:/.*)?$");

    private final RecordRouteRefusalUseCase record;

    ClinicalRouteRefusedListener(RecordRouteRefusalUseCase record) {
        this.record = record;
    }

    @EventListener
    void on(ClinicalRouteRefused refused) {
        record.handle(new RecordRouteRefusalUseCase.Command(refused.username(),
                refused.method() + " " + refused.path(), patientIn(refused.path())));
    }

    private static PatientId patientIn(String path) {
        Matcher matcher = RECORD.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new PatientId(UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
