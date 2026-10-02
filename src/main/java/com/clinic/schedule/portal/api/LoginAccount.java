package com.clinic.schedule.portal.api;

import java.util.UUID;

/** A web account in use, as logging in needs it: whose it is, and the hash of its password (D-80). */
public record LoginAccount(UUID accountId, UUID patientId, String passwordHash) {
}
