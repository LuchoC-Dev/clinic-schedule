package com.clinic.schedule.appointment.domain;

/**
 * Where an appointment stands. Cancelled and absent appointments are kept as records: the first
 * was called off, the second is the record that the patient did not come (D-61). Neither holds
 * its slot any more.
 */
public enum AppointmentStatus {
    BOOKED,
    CANCELLED,
    ABSENT
}
