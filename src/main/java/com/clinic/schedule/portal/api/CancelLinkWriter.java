package com.clinic.schedule.portal.api;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues the link to cancel an appointment from an email (D-83), for the reminders to carry. Kept as a writer
 * because issuing one keeps it (D-18).
 */
public interface CancelLinkWriter {

    /**
     * The address of a new link to cancel that appointment, booked at that time; none when there is no time
     * left to cancel it on the web, and the email is to say to call. It joins the caller's transaction.
     */
    Optional<String> linkFor(UUID appointmentId, LocalDateTime startsAt);
}
