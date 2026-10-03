package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;

/**
 * The appointment an email's link would cancel, as whoever opens it reads it (D-83): when, and with whom. Not
 * the patient's name, nor the reason: a link may be forwarded.
 */
record LinkedAppointmentResponse(LocalDateTime startsAt, String practitioner) {
}
