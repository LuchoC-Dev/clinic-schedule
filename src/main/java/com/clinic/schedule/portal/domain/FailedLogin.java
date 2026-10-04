package com.clinic.schedule.portal.domain;

/**
 * An account after a login with a wrong password (D-86), and the entry that records its lock when this one
 * locked it.
 *
 * @param locked null unless this failed login locked the account
 */
public record FailedLogin(PatientAccount account, AccountChange locked) {
}
