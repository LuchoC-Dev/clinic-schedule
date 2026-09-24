package com.clinic.schedule.appointment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What other modules may ask about appointments. The only way into this module besides its
 * endpoints and screens (D-24), and read-only, hence the name (D-18).
 *
 * <p>It answers facts, not what they mean to the caller: whether a checked-in appointment lets
 * a practitioner record a consultation is the consultation module's rule, not this one's (D-48).
 */
public interface AppointmentReader {

    /** The patient's booked appointments on that day, with any practitioner. Cancelled ones are left out. */
    List<AppointmentSummary> findBookedForPatientOn(UUID patientId, LocalDate date);
}
