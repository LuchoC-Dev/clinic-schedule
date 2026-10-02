package com.clinic.schedule.portal.adapter.in.http;

import jakarta.validation.constraints.NotBlank;

/** The body for changing a web account's email. */
record EmailChangeRequest(
        @NotBlank(message = "the email is required")
        String email) {
}
