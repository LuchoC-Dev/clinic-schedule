package com.clinic.schedule.practitioner;

import java.util.UUID;

/** A reason as the API returns it, alone or inside a practitioner. */
record ReasonResponse(UUID id, String name, int durationMinutes) {

    static ReasonResponse of(Reason reason) {
        return new ReasonResponse(reason.getId(), reason.getName(), reason.getDurationMinutes());
    }
}
