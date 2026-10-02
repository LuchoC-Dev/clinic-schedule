package com.clinic.schedule.portal.application.port;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;

/** The agenda, as the appointment module keeps it: the times it offers, and booking on the web (D-82). */
public interface BookingPort {

    /** The times the agenda offers for that reason with that practitioner on that day. */
    List<LocalTime> offeredTimes(UUID practitionerId, UUID reasonId, LocalDate date);

    /**
     * Books it with the appointment module's rules, marked as booked on the web.
     *
     * @return the new appointment's id
     * @throws WebBookingRefusedException when the time was taken meanwhile, or the patient is busy then
     */
    UUID book(UUID patientId, UUID practitionerId, UUID reasonId, LocalDateTime startsAt);
}
