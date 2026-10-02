package com.clinic.schedule.portal.application.port;

import java.util.List;

import com.clinic.schedule.portal.domain.BookableReason;

/** The practitioners' reasons, as the practitioner module keeps them (D-82). */
public interface BookableReasonsPort {

    /** Every practitioner's reasons, by practitioner, whether booked on the web or not. */
    List<BookableReason> all();
}
