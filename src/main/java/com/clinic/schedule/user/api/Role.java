package com.clinic.schedule.user.api;

/**
 * What a user does at the practice. Reception manages everything but the clinical record; a
 * practitioner reads their own agenda and works with clinical records (D-04, D-51); an admin
 * administers the users and nothing else (D-68). A user has one role, which never changes.
 *
 * <p>Part of the module's contract: the security module decides by it which routes each user
 * may reach (D-34, D-43).
 */
public enum Role {
    RECEPTION,
    PRACTITIONER,
    ADMIN
}
