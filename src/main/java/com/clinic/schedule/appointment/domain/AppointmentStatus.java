package com.clinic.schedule.appointment.domain;

/**
 * The only two states of version 1. Cancelled appointments are kept as records, because the
 * patient file will show them.
 */
public enum AppointmentStatus {
    BOOKED,
    CANCELLED
}
