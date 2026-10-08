package com.clinic.schedule.portal.domain.exception;

/**
 * Something asked of a family group that its rules refuse, and why (D-84). Maps to 409: the request is well
 * formed, and refused for the members' ages or the group as it stands.
 */
public class FamilyRefusedException extends RuntimeException {

    public enum Reason {
        /** Without a date of birth, nobody can tell which rules apply. */
        NO_BIRTH_DATE,
        /** Who joins by themselves is 13 or older; a child is added at a parent's request. */
        A_CHILD,
        /** A child under 13 is added as one; whoever is 13 or older joins by themselves. */
        NOT_A_CHILD,
        /** The patient is in the group already. */
        ALREADY_A_MEMBER,
        /** The patient is not in the group. */
        NOT_A_MEMBER,
        /** A child is added at the request of an adult of the group. */
        NOT_AN_ADULT_MEMBER,
        /** Reception has not seen what proves the request comes from a parent or guardian. */
        NO_PROOF,
        /** A child under 13 shares always: their parents act for them. */
        CHILDREN_SHARE,
        /** The member's relation is proved already, or they joined by themselves with none to prove (D-92). */
        NOTHING_TO_PROVE
    }

    private final Reason reason;

    public FamilyRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
