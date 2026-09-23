package com.clinic.schedule.appointment.application;

import java.util.Optional;

import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * Whose agenda the current user may read. Reception may read anyone's; a practitioner only
 * their own (D-04). A {@code Port}: it is another module, not storage (D-18).
 */
public interface AgendaAccessPort {

    /** The practitioner the user is limited to, or empty when the user may read every agenda. */
    Optional<PractitionerId> restrictedTo();
}
