package com.clinic.schedule.portal.adapter.in.http;

import jakarta.validation.constraints.NotBlank;

/** The body for activating a web account: the token of the email's link, and the chosen password. */
record ActivationRequest(
        @NotBlank(message = "the token is required")
        String token,

        @NotBlank(message = "the password is required")
        String password) {
}
