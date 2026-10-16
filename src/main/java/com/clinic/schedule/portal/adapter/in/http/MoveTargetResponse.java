package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An appointment as moving it starts from (D-93, D-111): as it stands — the same practitioner and reason, only the
 * time changes —, the member it is for, or null for the patient's own, whether it may still be moved on the web, and
 * the days it may be moved to.
 */
record MoveTargetResponse(UUID id, String member, LocalDateTime startsAt,
        OwnAppointmentsResponse.Practitioner practitioner, String reason, boolean movable, LocalDate firstDay,
        LocalDate lastDay) {
}
