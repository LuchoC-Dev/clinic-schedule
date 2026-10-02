package com.clinic.schedule.practitioner.presentation.http;

import java.util.UUID;

import com.clinic.schedule.practitioner.entity.Reason;

/** A reason as the API returns it, alone or inside a practitioner. */
record ReasonResponse(UUID id, String name, int durationMinutes, boolean webBookable) {

    static ReasonResponse of(Reason reason) {
        return new ReasonResponse(reason.getId(), reason.getName(), reason.getDurationMinutes(),
                reason.isWebBookable());
    }
}
