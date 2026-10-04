package com.clinic.schedule.portal.api;

import java.util.UUID;

/**
 * A web account in use, as logging in needs it: whose it is, the hash of its password (D-80), and whether too
 * many failed logins locked it for now (D-86).
 */
public record LoginAccount(UUID accountId, UUID patientId, String passwordHash, boolean locked) {
}
