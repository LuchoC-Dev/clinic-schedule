package com.clinic.schedule.arrival.session.http;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The appointment whose patient arrived (D-64). */
record AppointmentArrivalRequest(@NotNull(message = "the appointment is required") UUID appointmentId) {
}
