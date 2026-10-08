package com.clinic.schedule.portal.domain;

/**
 * A child under 13 an adult registers on the web (D-92), as they typed them, and what the adult is to them. The
 * child gives no phone: they are reached through the adult.
 */
public record RegisteredChild(Registrant person, FamilyMember.Relation relation) {

    public RegisteredChild {
        if (person == null) {
            throw new IllegalArgumentException("A child registered on the web is someone");
        }
        if (relation == null) {
            throw new IllegalArgumentException("A child is registered by their mother, father or guardian, named");
        }
    }

    public String fullName() {
        return person.fullName();
    }
}
