package com.clinic.schedule.portal.adapter.in.http;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The body for opening a web account: whose, and the account's own email (D-79). */
record OpenAccountRequest(
        @NotNull(message = "the patient is required")
        UUID patientId,

        @NotBlank(message = "the email is required")
        String email) {
}
