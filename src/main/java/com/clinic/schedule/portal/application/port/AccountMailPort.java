package com.clinic.schedule.portal.application.port;

import com.clinic.schedule.portal.domain.ActivationMessage;
import com.clinic.schedule.portal.domain.BookingConfirmation;
import com.clinic.schedule.portal.domain.PasswordResetMessage;
import com.clinic.schedule.portal.domain.RegistrationMessage;
import com.clinic.schedule.portal.domain.RegistrationRefusedMessage;

/**
 * Sends a web account's messages to its email (D-79). Apart from the reminders' sending (D-78): those reach
 * the patient's contact, these the account's own address, and each module keeps its own words. A registration
 * made on the web is answered at the address it gave, which becomes the account's once confirmed (D-91).
 */
public interface AccountMailPort {

    /** @throws RuntimeException when it could not go out */
    void sendActivation(ActivationMessage message);

    /** @throws RuntimeException when it could not go out */
    void sendBookingConfirmation(BookingConfirmation confirmation);

    /** @throws RuntimeException when it could not go out */
    void sendPasswordReset(PasswordResetMessage message);

    /** @throws RuntimeException when it could not go out */
    void sendRegistration(RegistrationMessage message);

    /** @throws RuntimeException when it could not go out */
    void sendRegistrationRefused(RegistrationRefusedMessage message);
}
