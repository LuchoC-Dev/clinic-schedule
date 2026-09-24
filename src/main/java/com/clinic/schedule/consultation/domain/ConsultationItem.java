package com.clinic.schedule.consultation.domain;

/**
 * One thing done or given in a consultation: a check, a prescription or a study order, in free
 * text. The text is kept without the blanks around it.
 */
public record ConsultationItem(ItemKind kind, String description) {

    /** As long as the column that stores it (D-38). */
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;

    public ConsultationItem {
        if (kind == null) {
            throw new IllegalArgumentException("An item of a consultation needs a kind");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("An item of a consultation needs a description");
        }
        description = description.strip();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "An item of a consultation is at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
    }
}
