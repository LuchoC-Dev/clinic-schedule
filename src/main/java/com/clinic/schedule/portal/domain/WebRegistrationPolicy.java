package com.clinic.schedule.portal.domain;

import java.time.Duration;
import java.time.LocalDateTime;

import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.AccountRefusedException.Reason;

/**
 * Who may register themselves on the web, and the only way to make a registration that waits for its
 * confirmation (D-17, D-91):
 *
 * <ul>
 *   <li>a date of birth, by which they are 18 or older on the day: a younger patient is registered at reception,
 *       or by their parents (phase 8, part 2);</li>
 *   <li>an email that can be the account's, kept in lower case as the account keeps it.</li>
 * </ul>
 *
 * <p>Whether the name, the document and the phone can be a patient's is the patient module's to say, and whether
 * the document is someone's already is never told on the web: neither is looked at here. It is handed what it
 * needs and reads nothing itself.
 */
public final class WebRegistrationPolicy {

    private WebRegistrationPolicy() {
    }

    /**
     * @param passwordHash the password chosen, already hashed
     * @param validFor how long the link to confirm it lasts
     * @throws IllegalArgumentException when there is no date of birth, or the email cannot be one
     * @throws AccountRefusedException when they are not 18 or older
     */
    public static WebRegistration.Issued request(Registrant registrant, String email, String passwordHash,
            LocalDateTime now, Duration validFor) {
        if (registrant.birthDate() == null) {
            throw new IllegalArgumentException("Registering on the web needs a date of birth");
        }
        if (!AccountOpeningService.isAdultOn(registrant.birthDate(), now.toLocalDate())) {
            throw new AccountRefusedException(Reason.UNDER_EIGHTEEN,
                    "Only a patient of " + AccountOpeningService.MIN_AGE_ON_THE_WEB + " or older registers on the web");
        }
        return WebRegistration.issue(registrant, PatientAccount.email(email), passwordHash, now, validFor);
    }
}
