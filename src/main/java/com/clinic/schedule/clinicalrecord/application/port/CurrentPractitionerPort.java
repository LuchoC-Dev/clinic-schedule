package com.clinic.schedule.clinicalrecord.application.port;

import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * Who is using the application: the practitioner the current user stands for, empty when the user is
 * not one (D-49), and the username they logged in as, which each reading of a record keeps (D-71).
 */
public interface CurrentPractitionerPort {

    Optional<PractitionerId> practitionerId();

    String username();
}
