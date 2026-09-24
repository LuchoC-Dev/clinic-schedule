package com.clinic.schedule.user.api;

/**
 * Reception manages everything; a practitioner only reads their own agenda (D-04).
 *
 * <p>Part of the module's contract: the security module decides by it which routes each user
 * may reach (D-34, D-43).
 */
public enum Role {
    RECEPTION,
    PRACTITIONER
}
