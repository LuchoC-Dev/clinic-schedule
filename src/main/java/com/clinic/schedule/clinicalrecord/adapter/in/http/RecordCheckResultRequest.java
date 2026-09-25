package com.clinic.schedule.clinicalrecord.adapter.in.http;

import com.clinic.schedule.clinicalrecord.domain.CheckResult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of a result recorded later. Its author and time are whoever sends it, and now (D-53). */
record RecordCheckResultRequest(
        @NotBlank(message = "the text is required")
        @Size(max = CheckResult.MAX_TEXT_LENGTH, message = "the text is at most 10000 characters")
        String text) {
}
