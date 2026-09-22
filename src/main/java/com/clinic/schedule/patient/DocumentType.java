package com.clinic.schedule.patient;

/**
 * The kinds of identity document the practice sees. Argentine names are kept as they are,
 * because that is what reception reads off the document in their hand.
 *
 * <p>{@code LC} and {@code LE} are the older booklets, still the only document some elderly
 * patients carry. {@code CI} covers provincial and Mercosur cards, and {@code PASSPORT}
 * covers foreign patients.
 */
enum DocumentType {

    DNI,
    LC,
    LE,
    CI,
    PASSPORT;

    /** Only the DNI is strictly numeric; the rest mix letters and digits. */
    boolean isNumeric() {
        return this == DNI;
    }
}
