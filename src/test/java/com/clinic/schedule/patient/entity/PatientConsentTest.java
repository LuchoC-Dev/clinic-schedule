package com.clinic.schedule.patient.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Consent to be reminded on each of the patient's channels, and who gave it (D-77). */
class PatientConsentTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 15);
    private static final Responsible MOTHER = new Responsible("Ana Pérez", Relation.MOTHER);
    private static final Phone OWN_PHONE = new Phone("1145556666", null);
    private static final Email MOTHERS_EMAIL = new Email("ana@mail.com", MOTHER);

    private static Patient withChannels(Phone phone, Email email) {
        return Patient.register("Luis Pérez", null, false, new PatientDetails(null, phone, email, null), TODAY);
    }

    @Test
    void aPatientStartsWithNoConsent() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);

        assertThat(patient.getPhoneConsent()).isNull();
        assertThat(patient.getEmailConsent()).isNull();
    }

    @Test
    void eachConsentGivenOrWithdrawnLeavesItsEntryWithWhoGaveIt() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);

        List<PatientDetailsChange> given = patient.changeConsent(Consent.PATIENT, Consent.OWNER, NOW, "recepcion");

        assertThat(patient.getPhoneConsent()).isEqualTo(Consent.PATIENT);
        assertThat(patient.getEmailConsent()).isEqualTo(Consent.OWNER);
        assertThat(given).extracting(PatientDetailsChange::getField)
                .containsExactly(PatientDetailsChange.Field.PHONE_CONSENT, PatientDetailsChange.Field.EMAIL_CONSENT);
        assertThat(given.get(1).getValueBefore()).isNull();
        assertThat(given.get(1).getValueAfter()).isEqualTo("OWNER");
        assertThat(given.get(1).getChangedBy()).isEqualTo("recepcion");
        assertThat(given.get(1).getChangedAt()).isEqualTo(NOW);

        List<PatientDetailsChange> withdrawn = patient.changeConsent(Consent.PATIENT, null, NOW, "recepcion");

        assertThat(patient.getEmailConsent()).isNull();
        assertThat(withdrawn).singleElement().satisfies(change -> {
            assertThat(change.getField()).isEqualTo(PatientDetailsChange.Field.EMAIL_CONSENT);
            assertThat(change.getValueBefore()).isEqualTo("OWNER");
            assertThat(change.getValueAfter()).isNull();
        });
    }

    @Test
    void thePatientMayConsentOnAResponsiblePersonsChannel() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);

        patient.changeConsent(null, Consent.PATIENT, NOW, "recepcion");

        assertThat(patient.getEmailConsent()).isEqualTo(Consent.PATIENT);
    }

    @Test
    void theOwnersConsentIsOnlyForAResponsiblePersonsChannel() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);

        assertThatThrownBy(() -> patient.changeConsent(Consent.OWNER, null, NOW, "recepcion"))
                .isInstanceOf(InvalidPatientDetailsException.class);
        assertThat(patient.getPhoneConsent()).isNull();
    }

    @Test
    void aConsentNeedsTheChannelItIsFor() {
        Patient patient = withChannels(OWN_PHONE, null);

        assertThatThrownBy(() -> patient.changeConsent(null, Consent.PATIENT, NOW, "recepcion"))
                .isInstanceOf(InvalidPatientDetailsException.class);
        Patient withoutPhone = withChannels(null, MOTHERS_EMAIL);
        assertThatThrownBy(() -> withoutPhone.changeConsent(Consent.PATIENT, null, NOW, "recepcion"))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }

    @Test
    void theSameConsentAgainLeavesNoTrace() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);
        patient.changeConsent(Consent.PATIENT, Consent.OWNER, NOW, "recepcion");

        assertThat(patient.changeConsent(Consent.PATIENT, Consent.OWNER, NOW, "recepcion")).isEmpty();
    }

    @Test
    void changingAChannelsNumberOrOwnerDropsItsConsent() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);
        patient.changeConsent(Consent.PATIENT, Consent.OWNER, NOW, "recepcion");

        Email sameAddressAnotherOwner = new Email("ana@mail.com", new Responsible("Ana Pérez", Relation.GUARDIAN));
        List<PatientDetailsChange> changes = patient.changeDetails(new PatientDetails(null,
                new Phone("1149998888", null), sameAddressAnotherOwner, null), TODAY, NOW, "recepcion");

        assertThat(patient.getPhoneConsent()).isNull();
        assertThat(patient.getEmailConsent()).isNull();
        assertThat(changes).extracting(PatientDetailsChange::getField).containsExactly(
                PatientDetailsChange.Field.PHONE, PatientDetailsChange.Field.EMAIL,
                PatientDetailsChange.Field.PHONE_CONSENT, PatientDetailsChange.Field.EMAIL_CONSENT);
        assertThat(changes.get(2).getValueBefore()).isEqualTo("PATIENT");
        assertThat(changes.get(2).getValueAfter()).isNull();
    }

    @Test
    void aChannelThatStaysAsItWasKeepsItsConsent() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);
        patient.changeConsent(Consent.PATIENT, Consent.OWNER, NOW, "recepcion");

        List<PatientDetailsChange> changes = patient.changeDetails(new PatientDetails(LocalDate.of(1981, 3, 12),
                new Phone("11 4555-6666", null), MOTHERS_EMAIL, null), TODAY, NOW, "recepcion");

        assertThat(patient.getPhoneConsent()).isEqualTo(Consent.PATIENT);
        assertThat(patient.getEmailConsent()).isEqualTo(Consent.OWNER);
        assertThat(changes).extracting(PatientDetailsChange::getField)
                .containsExactly(PatientDetailsChange.Field.BIRTH_DATE);
    }

    @Test
    void clearingAChannelDropsItsConsent() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);
        patient.changeConsent(Consent.PATIENT, Consent.OWNER, NOW, "recepcion");

        patient.changeDetails(new PatientDetails(null, OWN_PHONE, null, null), TODAY, NOW, "recepcion");

        assertThat(patient.getEmailConsent()).isNull();
        assertThat(patient.getPhoneConsent()).isEqualTo(Consent.PATIENT);
    }

    /** From their web account: yes is their own, no withdraws any, and nothing said leaves it (D-81). */
    @Test
    void thePatientAgreesOrNotOnEachChannelAndLeavesAloneWhatTheyDoNotSay() {
        Patient patient = withChannels(OWN_PHONE, MOTHERS_EMAIL);
        patient.changeConsent(null, Consent.OWNER, NOW, "recepcion");

        assertThat(patient.changeOwnConsent(true, null, NOW, "portal:cuenta")).singleElement()
                .satisfies(change -> {
                    assertThat(change.getField()).isEqualTo(PatientDetailsChange.Field.PHONE_CONSENT);
                    assertThat(change.getChangedBy()).isEqualTo("portal:cuenta");
                });
        assertThat(patient.getPhoneConsent()).isEqualTo(Consent.PATIENT);
        assertThat(patient.getEmailConsent()).isEqualTo(Consent.OWNER);

        patient.changeOwnConsent(false, false, NOW, "portal:cuenta");
        assertThat(patient.getPhoneConsent()).isNull();
        assertThat(patient.getEmailConsent()).isNull();

        patient.changeOwnConsent(null, true, NOW, "portal:cuenta");
        assertThat(patient.getPhoneConsent()).isNull();
        assertThat(patient.getEmailConsent()).isEqualTo(Consent.PATIENT);

        assertThatThrownBy(() -> withChannels(null, MOTHERS_EMAIL).changeOwnConsent(true, null, NOW, "portal:cuenta"))
                .isInstanceOf(InvalidPatientDetailsException.class);
    }

    /** What reception changes, with its rules; the date of birth stays reception's (D-81). */
    @Test
    void thePatientChangesTheirContactButNotTheirDateOfBirth() {
        Patient patient = Patient.register("Luis Pérez", null, false,
                new PatientDetails(LocalDate.of(1990, 5, 14), OWN_PHONE, MOTHERS_EMAIL, null), TODAY);
        patient.changeConsent(Consent.PATIENT, Consent.OWNER, NOW, "recepcion");

        List<PatientDetailsChange> changes = patient.changeContact(OWN_PHONE, new Email("luis@mail.com", null),
                new HealthInsurance("OSDE", "123", "310"), TODAY, NOW, "portal:cuenta");

        assertThat(patient.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(patient.getEmail()).isEqualTo(new Email("luis@mail.com", null));
        assertThat(patient.getInsurance()).isEqualTo(new HealthInsurance("OSDE", "123", "310"));
        assertThat(patient.getPhoneConsent()).isEqualTo(Consent.PATIENT);
        assertThat(patient.getEmailConsent()).isNull();
        assertThat(changes).extracting(PatientDetailsChange::getField).containsExactly(
                PatientDetailsChange.Field.EMAIL, PatientDetailsChange.Field.INSURANCE,
                PatientDetailsChange.Field.EMAIL_CONSENT);
        assertThat(changes).allSatisfy(change -> assertThat(change.getChangedBy()).isEqualTo("portal:cuenta"));
    }
}
