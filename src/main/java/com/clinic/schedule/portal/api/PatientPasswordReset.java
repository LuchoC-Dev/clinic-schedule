package com.clinic.schedule.portal.api;

import java.util.UUID;

/**
 * A patient chose a new password from the link in an email, published once it is saved (D-86). The security
 * module ends the account's open sessions, as it does a deactivated account's (D-79): whoever else was in it is
 * the reason to reset it.
 */
public record PatientPasswordReset(UUID accountId) {
}
