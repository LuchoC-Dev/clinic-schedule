package com.clinic.schedule.portal.domain;

import java.util.UUID;

/**
 * A reason one of the practice's practitioners sees patients for, as booking on the web needs it (D-82):
 * whose it is, how long it takes, and whether reception lets patients book it on the web.
 */
public record BookableReason(UUID practitionerId, String practitionerName, UUID reasonId, String reasonName,
        int durationMinutes, boolean webBookable) {
}
