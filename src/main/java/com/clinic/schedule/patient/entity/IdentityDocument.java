package com.clinic.schedule.patient.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * How a patient identifies themselves: which document, and its number. Optional — a patient
 * can be registered without one — but never half of it.
 *
 * <p>It is a value object and not a bare number because the number alone does not identify
 * anybody: DNI 30111222 and passport 30111222 are different people, and a unique constraint
 * on the number alone would refuse the second one.
 *
 * <p>It normalizes what reception types. Someone writing "30.111.222" and someone writing
 * "30111222" mean the same document, and if both got stored the duplicate check would not
 * see it.
 */
@Embeddable
public record IdentityDocument(

        @Enumerated(EnumType.STRING)
        @Column(name = "document_type", length = 20)
        DocumentType type,

        @Column(name = "document_number", length = 20)
        String number) {

    public IdentityDocument {
        if (type == null) {
            throw new IllegalArgumentException("A document needs a type");
        }
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("A document needs a number");
        }
        number = clean(number);
        if (number.isEmpty()) {
            throw new IllegalArgumentException("A document needs a number");
        }
        if (number.length() > MAX_NUMBER_LENGTH) {
            throw new IllegalArgumentException("A document number is at most " + MAX_NUMBER_LENGTH + " characters");
        }
        if (!type.accepts(number)) {
            throw new IllegalArgumentException("A " + type + " number has " + type.description());
        }
    }

    /** As long as the column that stores it (D-38). */
    static final int MAX_NUMBER_LENGTH = 20;

    /**
     * Builds a document, or nothing when neither part was given. Reception leaving both
     * fields empty is the ordinary case of a patient without a document, not an error.
     *
     * @throws IllegalArgumentException when only one of the two parts was given
     */
    public static IdentityDocument ofNullable(DocumentType type, String number) {
        boolean noType = type == null;
        boolean noNumber = number == null || number.isBlank();

        if (noType && noNumber) {
            return null;
        }
        if (noType || noNumber) {
            throw new IncompleteDocumentException();
        }
        return new IdentityDocument(type, number);
    }

    /** Separators reception types out of habit carry no meaning: dots, spaces, dashes. */
    private static String clean(String number) {
        return number.strip().toUpperCase().replaceAll("[.\s-]", "");
    }
}
