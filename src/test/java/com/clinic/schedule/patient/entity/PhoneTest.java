package com.clinic.schedule.patient.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneTest {

    private static final Responsible MOTHER = new Responsible("Ana Pérez", Relation.MOTHER);

    @Test
    void theSeparatorsReceptionTypesAreDropped() {
        assertThat(new Phone("(011) 4555-6666", null).number()).isEqualTo("01145556666");
        assertThat(new Phone(" +54 9 11 4555.6666 ", null).number()).isEqualTo("+5491145556666");
    }

    @Test
    void aNumberHasEightToFifteenDigits() {
        assertThat(new Phone("45556666", null).number()).isEqualTo("45556666");
        assertThat(new Phone("+123456789012345", null).number()).isEqualTo("+123456789012345");
        assertThatThrownBy(() -> new Phone("4555666", null)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> new Phone("1234567890123456", null)).isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void onlyDigitsAndALeadingPlus() {
        assertThatThrownBy(() -> new Phone("11 4555 CASA", null)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> new Phone("1145+556666", null)).isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void noNumberIsNoPhoneButAnOwnerNeedsOne() {
        assertThat(Phone.ofNullable("  ", null)).isNull();
        assertThat(Phone.ofNullable(null, null)).isNull();
        assertThatThrownBy(() -> Phone.ofNullable("", MOTHER)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThat(Phone.ofNullable("1145556666", MOTHER).owner()).isEqualTo(MOTHER);
    }
}
