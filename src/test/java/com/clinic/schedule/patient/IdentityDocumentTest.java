package com.clinic.schedule.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The document rules on their own: no Spring, no database (D-25). */
class IdentityDocumentTest {

    @Test
    void keepsTheTypeAndTheNumber() {
        IdentityDocument document = new IdentityDocument(DocumentType.DNI, "30111222");

        assertThat(document.type()).isEqualTo(DocumentType.DNI);
        assertThat(document.number()).isEqualTo("30111222");
    }

    @Test
    void theSameNumberUnderDifferentTypesIsADifferentDocument() {
        // This is why the value object exists: a unique constraint on the number alone would
        // refuse the second patient.
        assertThat(new IdentityDocument(DocumentType.DNI, "30111222"))
                .isNotEqualTo(new IdentityDocument(DocumentType.PASSPORT, "30111222"));
    }

    @Test
    void separatorsReceptionTypesOutOfHabitAreRemoved() {
        IdentityDocument withDots = new IdentityDocument(DocumentType.DNI, "30.111.222");
        IdentityDocument withSpaces = new IdentityDocument(DocumentType.DNI, " 30 111 222 ");

        assertThat(withDots.number()).isEqualTo("30111222");
        // Both spellings have to end up equal, or the duplicate check would not see them.
        assertThat(withSpaces).isEqualTo(withDots);
    }

    @Test
    void lettersAreStoredInUpperCase() {
        assertThat(new IdentityDocument(DocumentType.PASSPORT, "aa123456").number())
                .isEqualTo("AA123456");
    }

    @Test
    void aPassportMayMixLettersAndDigits() {
        assertThat(new IdentityDocument(DocumentType.PASSPORT, "AAB-123456").number())
                .isEqualTo("AAB123456");
    }

    @Test
    void aDniIsOnlyDigits() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IdentityDocument(DocumentType.DNI, "30111222A"))
                .withMessageContaining("DNI");
    }

    @Test
    void aDocumentNeedsBothParts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(null, "30111222"));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(DocumentType.DNI, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(DocumentType.DNI, "   "));
        // Separators only: nothing left once they are removed.
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(DocumentType.DNI, "..-.."));
    }

    @Test
    void noPartsAtAllIsAPatientWithoutADocument() {
        assertThat(IdentityDocument.ofNullable(null, null)).isNull();
        assertThat(IdentityDocument.ofNullable(null, "   ")).isNull();
    }

    @Test
    void halfADocumentIsRejected() {
        assertThatThrownBy(() -> IdentityDocument.ofNullable(DocumentType.DNI, null))
                .isInstanceOf(IncompleteDocumentException.class);
        assertThatThrownBy(() -> IdentityDocument.ofNullable(null, "30111222"))
                .isInstanceOf(IncompleteDocumentException.class);
    }

    @Test
    void aCompletePairBuildsTheDocument() {
        assertThat(IdentityDocument.ofNullable(DocumentType.LC, "1234567"))
                .isEqualTo(new IdentityDocument(DocumentType.LC, "1234567"));
    }

    // --- what each type of number looks like (D-38) ------------------------------------

    @Test
    void aDniHasSevenOrEightDigits() {
        assertThat(new IdentityDocument(DocumentType.DNI, "1.234.567").number()).isEqualTo("1234567");
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(DocumentType.DNI, "544"))
                .withMessage("A DNI number has 7 or 8 digits");
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(DocumentType.DNI, "123456789"));
    }

    @Test
    void theOlderBookletsHaveSixToEightDigits() {
        assertThat(new IdentityDocument(DocumentType.LE, "123456").number()).isEqualTo("123456");
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(DocumentType.LE, "AB12"));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(DocumentType.LC, "12345"));
    }

    @Test
    void aPassportOrACiIsLettersAndDigitsOnly() {
        assertThat(new IdentityDocument(DocumentType.CI, "uy-1234567").number()).isEqualTo("UY1234567");
        assertThatIllegalArgumentException().isThrownBy(() -> new IdentityDocument(DocumentType.PASSPORT, "AB#123"));
    }

    @Test
    void aNumberFitsItsColumn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IdentityDocument(DocumentType.PASSPORT, "A".repeat(21)))
                .withMessageContaining("at most 20");
    }
}
