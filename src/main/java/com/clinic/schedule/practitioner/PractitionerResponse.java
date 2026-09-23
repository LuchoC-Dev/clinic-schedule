package com.clinic.schedule.practitioner;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * A practitioner with everything reception configured: reasons by name, the week in the order
 * it is read, and the time off in the order it happens. Plain values, not the entity's types,
 * so the contract does not move when the model does (D-23).
 */
record PractitionerResponse(UUID id, String fullName, List<ReasonResponse> reasons, List<Hours> weeklySchedule,
        List<Absence> timeOff) {

    record Hours(DayOfWeek day, LocalTime startsAt, LocalTime endsAt) {
    }

    record Absence(LocalDateTime startsAt, LocalDateTime endsAt) {
    }

    static PractitionerResponse of(Practitioner practitioner) {
        return new PractitionerResponse(
                practitioner.getId(),
                practitioner.getFullName(),
                practitioner.getReasons().stream().map(ReasonResponse::of).toList(),
                practitioner.getWorkingHours().stream()
                        .map(stretch -> new Hours(stretch.day(), stretch.startsAt(), stretch.endsAt()))
                        .toList(),
                practitioner.getTimeOff().stream()
                        .map(absence -> new Absence(absence.startsAt(), absence.endsAt()))
                        .toList());
    }
}
