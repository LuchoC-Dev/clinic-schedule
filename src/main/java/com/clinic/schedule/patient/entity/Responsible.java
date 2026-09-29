package com.clinic.schedule.patient.entity;

/**
 * The person a phone or an email belongs to when it is not the patient's own: a child's mother, an
 * elderly patient's daughter (D-73). Only a name and what they are to the patient — not a patient,
 * nor anything the practice keeps on its own. Never required, a minor's included: a minor who comes
 * alone is seen.
 */
public record Responsible(String name, Relation relation) {

    public Responsible {
        if (name == null || name.isBlank() || relation == null) {
            throw new InvalidPatientDetailsException("A responsible person needs a name and a relation");
        }
        name = name.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new InvalidPatientDetailsException(
                    "A responsible person's name is at most " + MAX_NAME_LENGTH + " characters");
        }
        if (name.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidPatientDetailsException("A responsible person's name needs at least one letter");
        }
    }

    /** As long as the column that stores it, and as a patient's own name. */
    static final int MAX_NAME_LENGTH = 150;

    /**
     * The person, or nobody when neither part was given: the phone or the email is then the
     * patient's own.
     *
     * @throws InvalidPatientDetailsException when only one of the two parts was given
     */
    public static Responsible ofNullable(String name, Relation relation) {
        if ((name == null || name.isBlank()) && relation == null) {
            return null;
        }
        return new Responsible(name, relation);
    }
}
