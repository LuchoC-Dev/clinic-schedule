package com.clinic.schedule.security.entity;

/** Reception manages everything; a practitioner only reads their own agenda (D-04). */
public enum Role {

    RECEPTION,
    PRACTITIONER;

    /** The authority Spring Security checks: the role with its conventional prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
