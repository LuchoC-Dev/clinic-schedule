package com.clinic.schedule.reminder.adapter.out.external;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.api.CancelLinkWriter;
import com.clinic.schedule.reminder.application.port.CancelLinkPort;

/** Answers {@link CancelLinkPort} by asking the portal through its api (D-05). */
@Component
class CancelLinkAdapter implements CancelLinkPort {

    private final CancelLinkWriter links;

    CancelLinkAdapter(CancelLinkWriter links) {
        this.links = links;
    }

    @Override
    public Optional<String> linkFor(UUID appointmentId, LocalDateTime startsAt) {
        return links.linkFor(appointmentId, startsAt);
    }
}
