package com.clinic.schedule.portal.application.port;

import com.clinic.schedule.portal.domain.ActivationMessage;
import com.clinic.schedule.portal.domain.BookingConfirmation;

/**
 * Sends a web account's messages to its email (D-79). Apart from the reminders' sending (D-78): those reach
 * the patient's contact, these the account's own address, and each module keeps its own words.
 */
public interface AccountMailPort {

    /** @throws RuntimeException when it could not go out */
    void sendActivation(ActivationMessage message);

    /** @throws RuntimeException when it could not go out */
    void sendBookingConfirmation(BookingConfirmation confirmation);
}
