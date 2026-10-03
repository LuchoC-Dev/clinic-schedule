package com.clinic.schedule.portal.application.port;

import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.BookedAppointment;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException;

/** The appointment module, for cancelling a patient's appointment on the web or from an email's link (D-83). */
public interface AppointmentCancellationPort {

    /** The appointment, if it is still booked. */
    Optional<BookedAppointment> findBooked(UUID appointmentId);

    /** @throws WebCancellationRefusedException when it is not booked any more */
    void cancelOnWeb(UUID appointmentId);

    /** @throws WebCancellationRefusedException when it is not booked any more */
    void cancelFromEmailLink(UUID appointmentId);
}
