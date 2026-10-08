package com.clinic.schedule.arrival.session.http;

import java.math.BigDecimal;
import java.util.UUID;

import com.clinic.schedule.cashdesk.api.Means;

import jakarta.validation.constraints.NotNull;

/**
 * The appointment whose patient arrived (D-64), the pesos charged for the visit (D-87), and how they were paid —
 * left out, in cash (D-89).
 */
record AppointmentArrivalRequest(@NotNull(message = "the appointment is required") UUID appointmentId,
        BigDecimal amount, Means means) {
}
