package com.clinic.schedule.patient.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResponsibleTest {

    @Test
    void aResponsiblePersonIsANameAndARelation() {
        Responsible mother = new Responsible("  Ana Pérez ", Relation.MOTHER);

        assertThat(mother.name()).isEqualTo("Ana Pérez");
        assertThat(mother.relation()).isEqualTo(Relation.MOTHER);
    }

    @Test
    void neitherPartIsNobodyButOneAloneIsRefused() {
        assertThat(Responsible.ofNullable(" ", null)).isNull();
        assertThatThrownBy(() -> Responsible.ofNullable("Ana Pérez", null))
                .isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> Responsible.ofNullable("", Relation.FATHER))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void theNameHasALetterAndFitsItsColumn() {
        assertThatThrownBy(() -> new Responsible("123", Relation.CHILD)).isInstanceOf(InvalidPatientDetailsException.class);
        assertThat(new Responsible("a".repeat(150), Relation.OTHER).name()).hasSize(150);
        assertThatThrownBy(() -> new Responsible("a".repeat(151), Relation.OTHER))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }
}
