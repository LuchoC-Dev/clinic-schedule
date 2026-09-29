package com.clinic.schedule.patient.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** What is known of a patient besides who they are, and the history of its changes (D-73). */
class PatientDetailsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 15);
    private static final Responsible MOTHER = new Responsible("Ana Pérez", Relation.MOTHER);
    private static final Phone OWN_PHONE = new Phone("1145556666", null);
    private static final Email OWN_EMAIL = new Email("luis@mail.com", null);
    private static final HealthInsurance OSDE = new HealthInsurance("OSDE", "61234567801", "310");

    private static PatientDetails born(LocalDate birthDate) {
        return new PatientDetails(birthDate, null, null, null);
    }

    private static Patient registered(PatientDetails details) {
        return Patient.register("Luis Pérez", null, false, details, TODAY);
    }

    @Test
    void aPatientIsRegisteredWithWhatIsKnownOfThem() {
        PatientDetails details = new PatientDetails(LocalDate.of(1981, 3, 12), new Phone("1145556666", MOTHER),
                OWN_EMAIL, OSDE);

        Patient patient = registered(details);

        assertThat(patient.getDetails()).isEqualTo(details);
        assertThat(patient.getPhone().owner()).isEqualTo(MOTHER);
        assertThat(patient.getEmail().owner()).isNull();
        assertThat(Patient.register("Luis Pérez", null, false).getDetails()).isEqualTo(PatientDetails.NONE);
    }

    @Test
    void aDateOfBirthIsNotInTheFuture() {
        assertThat(registered(born(TODAY)).getBirthDate()).isEqualTo(TODAY);
        assertThatThrownBy(() -> registered(born(TODAY.plusDays(1)))).isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void aDateOfBirthIsAtMost130YearsAgo() {
        assertThat(registered(born(TODAY.minusYears(130))).getBirthDate()).isEqualTo(LocalDate.of(1896, 3, 10));
        assertThatThrownBy(() -> registered(born(TODAY.minusYears(130).minusDays(1))))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void eachDetailThatChangesLeavesItsOwnEntryWithWhoAndWhen() {
        Patient patient = registered(new PatientDetails(null, OWN_PHONE, OWN_EMAIL, null));

        List<PatientDetailsChange> changes = patient.changeDetails(
                new PatientDetails(LocalDate.of(1981, 3, 12), new Phone("1145556666", MOTHER), OWN_EMAIL, null),
                TODAY, NOW, "recepcion");

        assertThat(changes).extracting(PatientDetailsChange::getField)
                .containsExactly(PatientDetailsChange.Field.BIRTH_DATE, PatientDetailsChange.Field.PHONE);
        PatientDetailsChange born = changes.get(0);
        assertThat(born.getPatientId()).isEqualTo(patient.getId());
        assertThat(born.getChangedAt()).isEqualTo(NOW);
        assertThat(born.getChangedBy()).isEqualTo("recepcion");
        assertThat(born.getValueBefore()).isNull();
        assertThat(born.getValueAfter()).isEqualTo("1981-03-12");
        PatientDetailsChange phone = changes.get(1);
        assertThat(phone.getValueBefore()).isEqualTo("1145556666");
        assertThat(phone.getOwnerBefore()).isNull();
        assertThat(phone.getValueAfter()).isEqualTo("1145556666");
        assertThat(phone.getOwnerAfter()).isEqualTo(MOTHER);
        assertThat(patient.getPhone().owner()).isEqualTo(MOTHER);
    }

    @Test
    void clearingADetailKeepsWhatItWas() {
        Patient patient = registered(new PatientDetails(null, OWN_PHONE, new Email("ana@mail.com", MOTHER), null));

        List<PatientDetailsChange> changes = patient.changeDetails(PatientDetails.NONE, TODAY, NOW, "recepcion");

        assertThat(changes).extracting(PatientDetailsChange::getField)
                .containsExactly(PatientDetailsChange.Field.PHONE, PatientDetailsChange.Field.EMAIL);
        assertThat(changes.get(1).getValueBefore()).isEqualTo("ana@mail.com");
        assertThat(changes.get(1).getOwnerBefore()).isEqualTo(MOTHER);
        assertThat(changes.get(1).getValueAfter()).isNull();
        assertThat(patient.getDetails()).isEqualTo(PatientDetails.NONE);
    }

    @Test
    void savingTheSameDetailsLeavesNoTrace() {
        PatientDetails details = new PatientDetails(LocalDate.of(1981, 3, 12), new Phone("(011) 4555-6666", MOTHER),
                new Email("Luis@Mail.com", null), null);
        Patient patient = registered(details);

        assertThat(patient.changeDetails(new PatientDetails(LocalDate.of(1981, 3, 12), new Phone("01145556666", MOTHER),
                OWN_EMAIL, null), TODAY, NOW, "recepcion")).isEmpty();
    }

    @Test
    void aWrongDateOfBirthIsRefusedAndChangesNothing() {
        Patient patient = registered(new PatientDetails(null, OWN_PHONE, null, null));

        assertThatThrownBy(() -> patient.changeDetails(new PatientDetails(TODAY.plusYears(1), null, OWN_EMAIL, null),
                TODAY, NOW, "recepcion")).isInstanceOf(InvalidPatientDetailsException.class);
        assertThat(patient.getPhone()).isEqualTo(OWN_PHONE);
        assertThat(patient.getEmail()).isNull();
    }

    @Test
    void aPatientUnderAnAliasMayHaveDetailsToo() {
        Patient patient = Patient.registerUnidentified("NN-4821");

        patient.changeDetails(new PatientDetails(null, new Phone("1145556666", MOTHER), null, null), TODAY, NOW,
                "recepcion");

        assertThat(patient.getPhone().owner()).isEqualTo(MOTHER);
    }

    // --- health insurance (D-74) ------------------------------------------------------------

    @Test
    void aChangeOfInsuranceKeepsEachPartBeforeAndAfter() {
        Patient patient = registered(new PatientDetails(null, null, null, OSDE));

        List<PatientDetailsChange> changes = patient.changeDetails(
                new PatientDetails(null, null, null, new HealthInsurance("OSDE", "61234567801", "410")),
                TODAY, NOW, "recepcion");

        assertThat(changes).hasSize(1);
        PatientDetailsChange change = changes.get(0);
        assertThat(change.getField()).isEqualTo(PatientDetailsChange.Field.INSURANCE);
        assertThat(change.getInsuranceBefore()).isEqualTo(OSDE);
        assertThat(change.getInsuranceAfter()).isEqualTo(new HealthInsurance("OSDE", "61234567801", "410"));
        assertThat(patient.getInsurance().plan()).isEqualTo("410");
    }

    @Test
    void takingTheInsuranceAwayKeepsWhatItWas() {
        Patient patient = registered(new PatientDetails(null, null, null, OSDE));

        PatientDetailsChange change = patient.changeDetails(PatientDetails.NONE, TODAY, NOW, "recepcion").get(0);

        assertThat(change.getInsuranceBefore()).isEqualTo(OSDE);
        assertThat(change.getInsuranceAfter()).isNull();
        assertThat(patient.getInsurance()).isNull();
    }

    @Test
    void theSameInsuranceTypedAgainLeavesNoTrace() {
        Patient patient = registered(new PatientDetails(null, null, null, OSDE));

        assertThat(patient.changeDetails(new PatientDetails(null, null, null,
                new HealthInsurance(" OSDE ", "61234567801 ", "310")), TODAY, NOW, "recepcion")).isEmpty();
    }
}
