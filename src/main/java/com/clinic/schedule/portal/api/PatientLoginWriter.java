package com.clinic.schedule.portal.api;

import java.util.UUID;

/**
 * What the security module tells this one of a patient's logins (D-86), so failed ones in a row lock the
 * account for a while. Only of logins to an account in use: a document with none has nothing to count.
 */
public interface PatientLoginWriter {

    void loginFailed(UUID accountId);

    void loginSucceeded(UUID accountId);
}
