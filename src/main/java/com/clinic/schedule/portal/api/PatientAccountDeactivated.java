package com.clinic.schedule.portal.api;

import java.util.UUID;

/**
 * A web account was deactivated, published once it is saved (D-79). The security module ends its open
 * sessions, as it does a deactivated user's (D-68).
 *
 * <p>An event rather than a call, because the security module already depends on this one's api, and this
 * one calling it back would close a cycle.
 */
public record PatientAccountDeactivated(UUID accountId) {
}
