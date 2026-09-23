package com.clinic.schedule.patient;

import java.util.regex.Pattern;

/**
 * The kinds of identity document the practice sees. Argentine names are kept as they are,
 * because that is what reception reads off the document in their hand.
 *
 * <p>{@code LC} and {@code LE} are the older booklets, still the only document some elderly
 * patients carry. {@code CI} covers provincial and Mercosur cards, and {@code PASSPORT}
 * covers foreign patients.
 *
 * <p>Each type knows what its numbers look like, once separators are removed and letters
 * upper-cased (D-38): a DNI has 7 or 8 digits; the older booklets, 6 to 8; a CI or a passport,
 * letters and digits, since their formats depend on the issuing country.
 */
enum DocumentType {

    DNI("\\d{7,8}", "7 or 8 digits"),
    LC("\\d{6,8}", "6 to 8 digits"),
    LE("\\d{6,8}", "6 to 8 digits"),
    CI("[A-Z0-9]+", "letters and digits only"),
    PASSPORT("[A-Z0-9]+", "letters and digits only");

    private final Pattern format;
    private final String description;

    DocumentType(String format, String description) {
        this.format = Pattern.compile(format);
        this.description = description;
    }

    /** @param number already cleaned of separators and upper-cased */
    boolean accepts(String number) {
        return format.matcher(number).matches();
    }

    /** What a valid number of this type looks like, for the refusal message. */
    String description() {
        return description;
    }
}
