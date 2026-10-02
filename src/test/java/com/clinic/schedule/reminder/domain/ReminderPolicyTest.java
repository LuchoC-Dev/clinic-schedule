package com.clinic.schedule.reminder.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Who is reminded, and how they are spoken to (D-78). */
class ReminderPolicyTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 12);
    private static final LocalDate THIRTEEN_THAT_DAY = DAY.minusYears(13);

    private static ReminderPolicy.Decision decide(String name, LocalDate birthDate, String owner, Consent consent) {
        return ReminderPolicy.decide(new Contact(name, birthDate, "mail@mail.com", owner, consent), DAY);
    }

    @Test
    void withoutAnEmailOrAConsentNothingIsSent() {
        assertThat(ReminderPolicy.decide(new Contact("Luis Pérez", null, null, null, null), DAY).notSent())
                .isEqualTo(NotSentReason.NO_EMAIL);
        assertThat(ReminderPolicy.decide(Contact.UNKNOWN, DAY).notSent()).isEqualTo(NotSentReason.NO_EMAIL);
        assertThat(decide("Luis Pérez", null, null, null).notSent()).isEqualTo(NotSentReason.NO_CONSENT);
        assertThat(decide("Luis Pérez", null, "Ana Pérez", null).notSent()).isEqualTo(NotSentReason.NO_CONSENT);
    }

    @Test
    void onTheirOwnEmailThePatientIsGreetedAndRemindedOfTheirAppointment() {
        ReminderPolicy.Decision decision = decide("Luis Pérez", null, null, Consent.PATIENT);

        assertThat(decision.notSent()).isNull();
        assertThat(decision.recipient()).isEqualTo(new Recipient("mail@mail.com", "Luis Pérez", false, null));
    }

    @Test
    void aChildIsRemindedOnTheirParentsEmailWithTheParentsConsent() {
        ReminderPolicy.Decision decision = decide("Tomás Pérez", THIRTEEN_THAT_DAY.plusDays(1), "Ana Pérez",
                Consent.OWNER);

        assertThat(decision.recipient()).isEqualTo(new Recipient("mail@mail.com", "Ana Pérez", true, "Tomás Pérez"));
    }

    @Test
    void fromThirteenOnTheDayOfTheVisitTheParentsConsentIsNotEnough() {
        assertThat(decide("Tomás Pérez", THIRTEEN_THAT_DAY, "Ana Pérez", Consent.OWNER).notSent())
                .isEqualTo(NotSentReason.CONFIDENTIAL);
        assertThat(decide("Tomás Pérez", DAY.minusYears(40), "Ana Pérez", Consent.OWNER).notSent())
                .isEqualTo(NotSentReason.CONFIDENTIAL);
    }

    @Test
    void withoutADateOfBirthTheParentsConsentIsNotEnoughEither() {
        assertThat(decide("Tomás Pérez", null, "Ana Pérez", Consent.OWNER).notSent())
                .isEqualTo(NotSentReason.CONFIDENTIAL);
    }

    @Test
    void thePatientsOwnConsentIsEnoughOnSomeoneElsesEmailAtAnyAge() {
        ReminderPolicy.Decision decision = decide("Rosa Pérez", DAY.minusYears(80), "Marta Pérez", Consent.PATIENT);

        assertThat(decision.recipient()).isEqualTo(new Recipient("mail@mail.com", "Marta Pérez", true, "Rosa Pérez"));
    }

    @Test
    void aPatientUnderAnAliasIsNamedNowhere() {
        assertThat(decide(null, null, null, Consent.PATIENT).recipient())
                .isEqualTo(new Recipient("mail@mail.com", null, false, null));
        assertThat(decide(null, null, "Ana Pérez", Consent.PATIENT).recipient())
                .isEqualTo(new Recipient("mail@mail.com", "Ana Pérez", true, null));
    }
}
