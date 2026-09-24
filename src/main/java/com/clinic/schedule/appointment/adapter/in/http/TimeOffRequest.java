package com.clinic.schedule.appointment.adapter.in.http;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import com.clinic.schedule.appointment.application.usecase.RecordTimeOffUseCase;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

import jakarta.validation.constraints.NotNull;

/**
 * A practitioner's absence: a whole day when no times are given, or part of that day between
 * {@code startsAt} and {@code endsAt}. The context asks for "a day or a time range"; an
 * absence of several days is recorded one day at a time.
 */
record TimeOffRequest(
        @NotNull(message = "the practitioner is required")
        UUID practitionerId,

        @NotNull(message = "the date is required")
        LocalDate date,

        LocalTime startsAt,

        LocalTime endsAt) {

    /** @throws IllegalArgumentException when only one of the two times is given */
    RecordTimeOffUseCase.Command toCommand() {
        return new RecordTimeOffUseCase.Command(new PractitionerId(practitionerId), span());
    }

    private TimeSlot span() {
        if (startsAt == null && endsAt == null) {
            return new TimeSlot(date.atStartOfDay(), Duration.ofDays(1));
        }
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("Time off needs both a start and an end time, or neither for the whole day");
        }
        LocalDateTime from = date.atTime(startsAt);
        return new TimeSlot(from, Duration.between(from, date.atTime(endsAt)));
    }
}
