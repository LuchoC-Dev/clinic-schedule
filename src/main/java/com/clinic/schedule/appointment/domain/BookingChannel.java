package com.clinic.schedule.appointment.domain;

/**
 * Where an appointment was booked (D-82): at reception, or by the patient on the web. Kept with the
 * appointment, so reception tells the web's apart on the agenda; who booked it, beyond where, is not.
 */
public enum BookingChannel {
    RECEPTION,
    WEB
}
