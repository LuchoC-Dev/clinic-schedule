package com.clinic.schedule.patient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body for registering or updating a patient. The national ID is optional; sending it
 * blank is the same as not sending it.
 */
record PatientRequest(
        @NotBlank(message = "the full name is required")
        @Size(max = 150, message = "the full name is at most 150 characters")
        String fullName,

        @Size(max = 20, message = "the national ID is at most 20 characters")
        String nationalId) {
}
