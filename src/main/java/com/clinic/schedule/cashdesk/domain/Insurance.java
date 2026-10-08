package com.clinic.schedule.cashdesk.domain;

/**
 * A patient's health insurance as the cash desk keeps it with a covered visit (D-90): the insurer, and the
 * member number and plan, as they were that day. Free text, as the patient's own (D-74), already checked where
 * it was typed; the insurer is always there.
 *
 * @param memberNumber null when the patient has none recorded
 * @param plan null when the patient has none recorded
 */
public record Insurance(String insurer, String memberNumber, String plan) {

    public Insurance {
        if (insurer == null || insurer.isBlank()) {
            throw new IllegalArgumentException("An insurance has its insurer");
        }
    }
}
