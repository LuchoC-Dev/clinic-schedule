package com.clinic.schedule.patient.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HealthInsuranceTest {

    @Test
    void anInsuranceIsTheInsurerWithAnOptionalMemberNumberAndPlan() {
        HealthInsurance insurance = new HealthInsurance(" PAMI ", " 15012345678900 ", "  ");

        assertThat(insurance.insurer()).isEqualTo("PAMI");
        assertThat(insurance.memberNumber()).isEqualTo("15012345678900");
        assertThat(insurance.plan()).isNull();
    }

    @Test
    void nothingGivenIsNoInsuranceButAMemberNumberOrAPlanNeedTheInsurer() {
        assertThat(HealthInsurance.ofNullable(" ", null, "")).isNull();
        assertThatThrownBy(() -> HealthInsurance.ofNullable("", "61234567801", null))
                .isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> HealthInsurance.ofNullable(null, null, "310"))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void theInsurersNameHasALetter() {
        assertThatThrownBy(() -> new HealthInsurance("123", null, null)).isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void eachPartFitsItsColumn() {
        assertThat(new HealthInsurance("a".repeat(100), "1".repeat(30), "p".repeat(50)).insurer()).hasSize(100);
        assertThatThrownBy(() -> new HealthInsurance("a".repeat(101), null, null))
                .isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> new HealthInsurance("OSDE", "1".repeat(31), null))
                .isInstanceOf(InvalidPatientDetailsException.class);
        assertThatThrownBy(() -> new HealthInsurance("OSDE", null, "p".repeat(51)))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }
}
