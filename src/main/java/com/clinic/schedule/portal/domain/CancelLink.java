package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The link to cancel an appointment that goes in its reminders and in the confirmation of a web booking
 * (D-83): a {@link SecretToken}, of which only the hash is kept, the appointment and the time it was about.
 * Nothing personal goes in the address (D-75).
 *
 * <p>It has no expiry of its own: it works while its appointment is still booked at that time and while
 * there is time to cancel it, so moving the appointment ends it — its new reminder brings another.
 *
 * @param hash the SHA-256 of the token, in hexadecimal
 */
public record CancelLink(String hash, UUID appointmentId, LocalDateTime startsAt) {

    public CancelLink {
        if (hash == null || appointmentId == null || startsAt == null) {
            throw new IllegalArgumentException("A cancel link needs its hash, its appointment and its time");
        }
    }

    /** The token, to go in the link and nowhere else, and what is kept of it. */
    public record Issued(String token, CancelLink stored) {
    }

    public static Issued issue(UUID appointmentId, LocalDateTime startsAt) {
        String token = SecretToken.generate();
        return new Issued(token, new CancelLink(SecretToken.hashOf(token), appointmentId, startsAt));
    }

    /** Whether it is about that appointment as it now stands: still booked at the time it was sent for. */
    public boolean isFor(BookedAppointment appointment) {
        return appointment.id().equals(appointmentId) && appointment.startsAt().equals(startsAt);
    }
}
