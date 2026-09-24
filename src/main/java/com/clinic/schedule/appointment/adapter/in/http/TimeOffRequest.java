package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.clinic.schedule.appointment.application.usecase.RecordTimeOffUseCase;
import com.clinic.schedule.appointment.domain.PractitionerId;

import jakarta.validation.constraints.NotNull;

/**
 * A practitioner's absence: whole days from {@code date} until {@code untilDate} when no times
 * are given, or part of a single day between {@code startsAt} and {@code endsAt} (D-46).
 * {@code untilDate} is optional; without it the absence is that one day.
 */
record TimeOffRequest(
        @NotNull(message = "the practitioner is required")
        UUID practitionerId,

        @NotNull(message = "the date is required")
        LocalDate date,

        LocalDate untilDate,

        LocalTime startsAt,

        LocalTime endsAt) {

    /** @throws IllegalArgumentException when the days and times do not describe an absence */
    RecordTimeOffUseCase.Command toCommand() {
        return RecordTimeOffUseCase.Command.of(new PractitionerId(practitionerId), date, untilDate, startsAt, endsAt);
    }
}
