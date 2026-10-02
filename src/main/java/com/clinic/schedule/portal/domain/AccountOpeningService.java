package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.time.Period;

import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.AccountRefusedException.Reason;

/**
 * Whether a web account may be opened for a patient, and the only way to open one (D-17, D-79):
 *
 * <ul>
 *   <li>one account per patient, whatever state the one they have is in;</li>
 *   <li>a verified identity: the account logs in with the patient's document, so reception must have seen
 *       it;</li>
 *   <li>13 or older on the day it is opened: from 13 a patient decides alone on much of their own care
 *       (Código Civil y Comercial, art. 26), and a younger one is booked for by their parents. Without a date
 *       of birth nobody can tell, so none is opened until reception records it.</li>
 * </ul>
 *
 * <p>It is handed what it needs and reads nothing itself: fetching it is the use case's job.
 */
public final class AccountOpeningService {

    /** The age from which a patient may have an account of their own. */
    static final int MIN_AGE = 13;

    private AccountOpeningService() {
    }

    /**
     * @param alreadyOpened whether the patient has an account, in any state
     * @param by the username of whoever at reception opens it
     * @throws AccountRefusedException when the patient may not have one
     */
    public static AccountUpdate open(AccountHolder holder, boolean alreadyOpened, String email, LocalDateTime at,
            String by) {
        if (alreadyOpened) {
            throw new AccountRefusedException(Reason.ALREADY_OPENED, "The patient has a web account already");
        }
        if (!holder.identityVerified()) {
            throw new AccountRefusedException(Reason.IDENTITY_NOT_VERIFIED,
                    "A web account needs the patient's identity verified");
        }
        if (holder.birthDate() == null) {
            throw new AccountRefusedException(Reason.NO_BIRTH_DATE, "A web account needs the patient's date of birth");
        }
        if (Period.between(holder.birthDate(), at.toLocalDate()).getYears() < MIN_AGE) {
            throw new AccountRefusedException(Reason.UNDER_THIRTEEN,
                    "A web account is for patients of " + MIN_AGE + " or older");
        }
        return PatientAccount.open(holder.patientId(), email, at, by);
    }
}
