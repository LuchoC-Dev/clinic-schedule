package com.clinic.schedule.reminder.application.port;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** The portal, which issues the link to cancel an appointment from its reminder (D-83). */
public interface CancelLinkPort {

    /** The address of the link; none when there is no time left to cancel it on the web. */
    Optional<String> linkFor(UUID appointmentId, LocalDateTime startsAt);
}
