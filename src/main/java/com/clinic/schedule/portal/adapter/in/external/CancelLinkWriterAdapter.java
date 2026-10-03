package com.clinic.schedule.portal.adapter.in.external;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.api.CancelLinkWriter;
import com.clinic.schedule.portal.application.usecase.CancelLinkUseCase;

/** Answers {@link CancelLinkWriter} for other modules, writing the link where the patients reach the application. */
@Component
class CancelLinkWriterAdapter implements CancelLinkWriter {

    private final CancelLinkUseCase links;
    private final String baseUrl;

    CancelLinkWriterAdapter(CancelLinkUseCase links, @Value("${app.portal.base-url}") String baseUrl) {
        this.links = links;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public Optional<String> linkFor(UUID appointmentId, LocalDateTime startsAt) {
        return links.issue(appointmentId, startsAt).map(token -> baseUrl + "/portal/cancel?token=" + token);
    }
}
