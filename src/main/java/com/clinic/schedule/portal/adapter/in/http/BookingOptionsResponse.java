package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.portal.application.usecase.ViewWebBookingUseCase;
import com.clinic.schedule.portal.domain.BookableReason;

/**
 * What a patient may book on the web (D-82): the days, and each practitioner with the reasons booked on the
 * web. A practitioner with none is left out.
 */
record BookingOptionsResponse(LocalDate firstDay, LocalDate lastDay, List<Practitioner> practitioners) {

    record Practitioner(UUID id, String fullName, List<Reason> reasons) {
    }

    record Reason(UUID id, String name, int durationMinutes) {
    }

    static BookingOptionsResponse of(ViewWebBookingUseCase.Options options) {
        Map<UUID, Practitioner> byPractitioner = new LinkedHashMap<>();
        for (BookableReason reason : options.reasons()) {
            byPractitioner.computeIfAbsent(reason.practitionerId(),
                            id -> new Practitioner(id, reason.practitionerName(), new ArrayList<>()))
                    .reasons().add(new Reason(reason.reasonId(), reason.reasonName(), reason.durationMinutes()));
        }
        return new BookingOptionsResponse(options.firstDay(), options.lastDay(), List.copyOf(byPractitioner.values()));
    }
}
