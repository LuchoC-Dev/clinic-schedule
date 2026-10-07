package com.clinic.schedule.arrival.session.http;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The appointment whose patient arrived (D-64), and the pesos charged for the visit (D-87). */
record AppointmentArrivalRequest(@NotNull(message = "the appointment is required") UUID appointmentId,
        BigDecimal amount) {
}
