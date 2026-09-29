package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.application.port.RecordRefusalWriter;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;

/**
 * A clinical route was refused to the user's role — reception, the admin or the waiting room asking
 * for a record, a file, a consultation — and is recorded (D-72). The security module refuses it and
 * says so; this module keeps the refusals of what is its own.
 */
@Service
public class RecordRouteRefusalUseCase {

    private final RecordRefusalWriter refusals;
    private final CurrentPractitionerPort currentPractitioner;
    private final Clock clock;

    public RecordRouteRefusalUseCase(RecordRefusalWriter refusals, CurrentPractitionerPort currentPractitioner,
            Clock clock) {
        this.refusals = refusals;
        this.currentPractitioner = currentPractitioner;
        this.clock = clock;
    }

    @Transactional
    public void handle(Command command) {
        refusals.save(RecordRefusal.ofRoute(command.refusedBy(), currentPractitioner.practitionerId().orElse(null),
                command.route(), command.patientId(), LocalDateTime.now(clock)));
    }

    /**
     * @param route the method and the path
     * @param patientId the patient the route names, or null when it names none
     */
    public record Command(String refusedBy, String route, PatientId patientId) {
    }
}
