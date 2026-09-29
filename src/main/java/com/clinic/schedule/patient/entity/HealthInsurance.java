package com.clinic.schedule.patient.entity;

/**
 * The health insurance a patient is covered by (D-74): the insurer — an obra social or a prepaid plan
 * —, the patient's member number and their plan. Free text, since every insurer has a format of its
 * own; only the length is checked, and that the insurer's name has a letter (D-38).
 *
 * <p>The member number is the patient's and is kept. The token some insurers ask for is generated anew
 * for each visit, so it is not the patient's and is not kept here.
 *
 * @param memberNumber null when not given
 * @param plan null when not given
 */
public record HealthInsurance(String insurer, String memberNumber, String plan) {

    public HealthInsurance {
        insurer = blankToNull(insurer);
        memberNumber = blankToNull(memberNumber);
        plan = blankToNull(plan);
        if (insurer == null) {
            throw new InvalidPatientDetailsException("A health insurance needs the insurer's name");
        }
        if (insurer.length() > MAX_INSURER_LENGTH) {
            throw new InvalidPatientDetailsException("An insurer's name is at most " + MAX_INSURER_LENGTH + " characters");
        }
        if (insurer.codePoints().noneMatch(Character::isLetter)) {
            throw new InvalidPatientDetailsException("An insurer's name needs at least one letter");
        }
        if (memberNumber != null && memberNumber.length() > MAX_MEMBER_NUMBER_LENGTH) {
            throw new InvalidPatientDetailsException(
                    "A member number is at most " + MAX_MEMBER_NUMBER_LENGTH + " characters");
        }
        if (plan != null && plan.length() > MAX_PLAN_LENGTH) {
            throw new InvalidPatientDetailsException("A plan is at most " + MAX_PLAN_LENGTH + " characters");
        }
    }

    /** As long as the columns that store them. */
    static final int MAX_INSURER_LENGTH = 100;
    static final int MAX_MEMBER_NUMBER_LENGTH = 30;
    static final int MAX_PLAN_LENGTH = 50;

    /**
     * The insurance, or none when nothing of it was given.
     *
     * @throws InvalidPatientDetailsException when a member number or a plan comes without the insurer
     */
    public static HealthInsurance ofNullable(String insurer, String memberNumber, String plan) {
        if (blankToNull(insurer) == null && blankToNull(memberNumber) == null && blankToNull(plan) == null) {
            return null;
        }
        return new HealthInsurance(insurer, memberNumber, plan);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
