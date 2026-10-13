package com.clinic.schedule.portal.application.port;

import java.util.UUID;

/** A practitioner's name, as the practitioner module keeps it, for the email that confirms a move (D-93). */
public interface PractitionerNamePort {

    /** @return the practitioner's full name; empty when there is no such practitioner */
    String nameOf(UUID practitionerId);
}
