package com.clinic.schedule.clinicalrecord.application.port;

import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/** The practitioner the current user stands for; empty when the user is not one (D-49). */
public interface CurrentPractitionerPort {

    Optional<PractitionerId> practitionerId();
}
