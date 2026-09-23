package com.clinic.schedule.security;

/** Reception manages everything; a practitioner only reads their own agenda (D-04). */
enum Role {

    RECEPTION,
    PRACTITIONER;

    /** The authority Spring Security checks: the role with its conventional prefix. */
    String authority() {
        return "ROLE_" + name();
    }
}
