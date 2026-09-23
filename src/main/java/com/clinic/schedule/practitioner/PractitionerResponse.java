package com.clinic.schedule.practitioner;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * A practitioner with everything reception configured: reasons by name, and the week in the
 * order it is read. Plain values, not the entity's types, so the contract does not move when
 * the model does (D-23).
 */
record PractitionerResponse(UUID id, String fullName, List<ReasonResponse> reasons, List<Hours> weeklySchedule) {

    record Hours(DayOfWeek day, LocalTime startsAt, LocalTime endsAt) {
    }

    static PractitionerResponse of(Practitioner practitioner) {
        return new PractitionerResponse(
                practitioner.getId(),
                practitioner.getFullName(),
                practitioner.getReasons().stream().map(ReasonResponse::of).toList(),
                practitioner.getWorkingHours().stream()
                        .map(stretch -> new Hours(stretch.day(), stretch.startsAt(), stretch.endsAt()))
                        .toList());
    }
}
