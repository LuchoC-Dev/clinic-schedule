package com.clinic.schedule.patient.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

    private static final Responsible FATHER = new Responsible("Luis Gómez", Relation.FATHER);

    @Test
    void anEmailIsKeptTrimmedAndInLowerCase() {
        assertThat(new Email("  Ana.Perez@Mail.COM ", null).address()).isEqualTo("ana.perez@mail.com");
        assertThat(new Email("ana@mail.com", null)).isEqualTo(new Email("ANA@MAIL.COM", null));
    }

    @Test
    void anEmailHasANameAnAtSignAndADomainWithADot() {
        assertThatThrownBy(() -> new Email("ana", null)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> new Email("ana@mail", null)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> new Email("@mail.com", null)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> new Email("ana perez@mail.com", null)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> new Email("ana@@mail.com", null)).isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void anEmailIsAtMost254Characters() {
        String domain = "@mail.com";
        assertThat(new Email("a".repeat(254 - domain.length()) + domain, null).address()).hasSize(254);
        assertThatThrownBy(() -> new Email("a".repeat(255 - domain.length()) + domain, null))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void noAddressIsNoEmailButAnOwnerNeedsOne() {
        assertThat(Email.ofNullable(" ", null)).isNull();
        assertThatThrownBy(() -> Email.ofNullable(null, FATHER)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThat(Email.ofNullable("luis@mail.com", FATHER).owner()).isEqualTo(FATHER);
    }
}
