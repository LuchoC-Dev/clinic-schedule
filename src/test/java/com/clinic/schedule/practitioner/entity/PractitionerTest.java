package com.clinic.schedule.practitioner.entity;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The rules the practitioner guards over its own data. It is a JPA entity, but nothing here
 * needs JPA: it is built with its factory and exercised directly.
 */
class PractitionerTest {

    private static WorkingHours hours(java.time.DayOfWeek day, String from, String to) {
        return new WorkingHours(day, LocalTime.parse(from), LocalTime.parse(to));
    }

    @Test
    void aNewPractitionerHasNoReasonsAndNoHours() {
        Practitioner practitioner = Practitioner.register("  Dra. Arias  ");

        assertThat(practitioner.getId()).isNotNull();
        assertThat(practitioner.getFullName()).isEqualTo("Dra. Arias");
        assertThat(practitioner.getReasons()).isEmpty();
        assertThat(practitioner.getWorkingHours()).isEmpty();
    }

    @Test
    void aPractitionerNeedsAName() {
        assertThatIllegalArgumentException().isThrownBy(() -> Practitioner.register("  "));
    }

    @Test
    void addsReasonsWithTheirDuration() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");

        Reason consulta = practitioner.addReason("consulta", 30);

        assertThat(consulta.getName()).isEqualTo("consulta");
        assertThat(consulta.getDurationMinutes()).isEqualTo(30);
        assertThat(practitioner.getReasons()).containsExactly(consulta);
    }

    @Test
    void twoReasonsCannotShareANameEvenInAnotherCase() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");
        practitioner.addReason("consulta", 30);

        assertThatThrownBy(() -> practitioner.addReason("  Consulta ", 45))
                .isInstanceOf(DuplicateReasonException.class);
    }

    @Test
    void aReasonNeedsAPositiveDuration() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");

        assertThatIllegalArgumentException().isThrownBy(() -> practitioner.addReason("consulta", 0));
    }

    @Test
    void changingAReasonKeepsItsIdentity() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");
        Reason reason = practitioner.addReason("control", 20);

        Reason changed = practitioner.changeReason(reason.getId(), "control anual", 40);

        assertThat(changed.getId()).isEqualTo(reason.getId());
        assertThat(changed.getName()).isEqualTo("control anual");
        assertThat(changed.getDurationMinutes()).isEqualTo(40);
    }

    @Test
    void aReasonCanKeepItsOwnNameWhenOnlyTheDurationChanges() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");
        Reason reason = practitioner.addReason("consulta", 30);

        Reason changed = practitioner.changeReason(reason.getId(), "Consulta", 45);

        assertThat(changed.getDurationMinutes()).isEqualTo(45);
    }

    @Test
    void aReasonCannotTakeTheNameOfAnother() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");
        practitioner.addReason("consulta", 30);
        Reason control = practitioner.addReason("control", 20);

        assertThatThrownBy(() -> practitioner.changeReason(control.getId(), "consulta", 20))
                .isInstanceOf(DuplicateReasonException.class);
    }

    @Test
    void changingAnUnknownReasonFails() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");

        assertThatThrownBy(() -> practitioner.changeReason(UUID.randomUUID(), "consulta", 30))
                .isInstanceOf(ReasonNotFoundException.class);
    }

    @Test
    void theWeekIsReplacedWholeAndReadInOrder() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");
        practitioner.replaceWeeklySchedule(List.of(hours(MONDAY, "09:00", "13:00")));

        practitioner.replaceWeeklySchedule(List.of(
                hours(TUESDAY, "14:00", "18:00"),
                hours(MONDAY, "14:00", "18:00"),
                hours(MONDAY, "08:00", "12:00")));

        assertThat(practitioner.getWorkingHours()).containsExactly(
                hours(MONDAY, "08:00", "12:00"),
                hours(MONDAY, "14:00", "18:00"),
                hours(TUESDAY, "14:00", "18:00"));
    }

    @Test
    void aRejectedWeekLeavesThePreviousOneInPlace() {
        Practitioner practitioner = Practitioner.register("Dra. Arias");
        practitioner.replaceWeeklySchedule(List.of(hours(MONDAY, "09:00", "13:00")));

        assertThatThrownBy(() -> practitioner.replaceWeeklySchedule(List.of(
                hours(MONDAY, "09:00", "13:00"),
                hours(MONDAY, "12:00", "18:00"))))
                .isInstanceOf(InvalidWeeklyScheduleException.class);

        assertThat(practitioner.getWorkingHours()).containsExactly(hours(MONDAY, "09:00", "13:00"));
    }
}
