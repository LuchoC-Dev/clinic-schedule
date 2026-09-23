package com.clinic.schedule.appointment.application;

import com.clinic.schedule.appointment.domain.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * The slot does not fall inside the practitioner's working hours. A booking rule saying no,
 * like an overlap, so it is one of them and answers 409: the request was well formed and
 * another time may work.
 *
 * <p>It is raised by the use case and not by the domain because the domain does not know
 * the week — that belongs to the practitioner module, which answers it (D-08).
 */
public class OutsideWorkingHoursException extends BookingRuleViolationException {

    public OutsideWorkingHoursException(PractitionerId practitionerId, TimeSlot slot) {
        super("Practitioner %s does not work from %s to %s"
                .formatted(practitionerId.value(), slot.startsAt(), slot.endsAt()));
    }
}
