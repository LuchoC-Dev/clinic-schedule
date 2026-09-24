package com.clinic.schedule.practitioner.presentation.http;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.clinic.schedule.practitioner.entity.WorkingHours;

/**
 * The whole week, sent at once. An empty list is valid: it leaves the practitioner with no
 * working hours, which is how a practitioner who stopped working is recorded.
 *
 * <p>Wrapped in an object instead of being a bare JSON array so each stretch is validated,
 * and so the body can grow a field later without changing its shape.
 */
record WeeklyScheduleRequest(
        @NotNull(message = "the hours are required")
        List<@Valid @NotNull Hours> hours) {

    record Hours(
            @NotNull(message = "the day is required")
            DayOfWeek day,

            @NotNull(message = "the start time is required")
            LocalTime startsAt,

            @NotNull(message = "the end time is required")
            LocalTime endsAt) {
    }

    List<WorkingHours> toWorkingHours() {
        return hours.stream()
                .map(stretch -> new WorkingHours(stretch.day(), stretch.startsAt(), stretch.endsAt()))
                .toList();
    }
}
