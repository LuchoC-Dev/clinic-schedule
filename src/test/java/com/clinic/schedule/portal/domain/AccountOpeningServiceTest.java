package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.AccountRefusedException.Reason;

/** Who may have a web account (D-79): one each, verified, and 13 or older on the day it is opened. */
class AccountOpeningServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final UUID PATIENT = UUID.randomUUID();

    private static AccountHolder holder(boolean verified, LocalDate birthDate) {
        return new AccountHolder(PATIENT, "Ana Pérez", verified, birthDate, false);
    }

    private static Reason refusal(AccountHolder holder, boolean alreadyOpened) {
        try {
            AccountOpeningService.open(holder, alreadyOpened, "ana@mail.com", NOW, "recepcion");
        } catch (AccountRefusedException e) {
            return e.reason();
        }
        throw new AssertionError("It was opened");
    }

    @Test
    void aVerifiedPatientOfThirteenOrOlderGetsAnAccountWaitingForTheirPassword() {
        AccountUpdate opened = AccountOpeningService.open(holder(true, LocalDate.of(1990, 5, 14)), false,
                "ana@mail.com", NOW, "recepcion");

        assertThat(opened.account().patientId()).isEqualTo(PATIENT);
        assertThat(opened.account().status()).isEqualTo(AccountStatus.PENDING);
        assertThat(opened.account().passwordHash()).isNull();
        assertThat(opened.account().openedAt()).isEqualTo(NOW);
        assertThat(opened.change().action()).isEqualTo(AccountAction.OPENED);
        assertThat(opened.change().accountId()).isEqualTo(opened.account().id());
        assertThat(opened.change().by()).isEqualTo("recepcion");
        assertThat(opened.change().at()).isEqualTo(NOW);
    }

    @Test
    void thirteenIsCountedOnTheDayItIsOpened() {
        assertThat(AccountOpeningService.open(holder(true, LocalDate.of(2013, 3, 10)), false, "ana@mail.com", NOW,
                "recepcion").account().status()).isEqualTo(AccountStatus.PENDING);
        assertThat(refusal(holder(true, LocalDate.of(2013, 3, 11)), false)).isEqualTo(Reason.UNDER_THIRTEEN);
    }

    @Test
    void anUnverifiedPatientOrOneWithoutADateOfBirthGetsNone() {
        assertThat(refusal(holder(false, LocalDate.of(1990, 5, 14)), false)).isEqualTo(Reason.IDENTITY_NOT_VERIFIED);
        assertThat(refusal(holder(true, null), false)).isEqualTo(Reason.NO_BIRTH_DATE);
    }

    @Test
    void aPatientHasOneAccountAtMost() {
        assertThat(refusal(holder(true, LocalDate.of(1990, 5, 14)), true)).isEqualTo(Reason.ALREADY_OPENED);
    }

    @Test
    void theEmailIsCheckedForItsFormAndKeptInLowerCase() {
        assertThat(AccountOpeningService.open(holder(true, LocalDate.of(1990, 5, 14)), false, "  Ana@Mail.COM ",
                NOW, "recepcion").account().email()).isEqualTo("ana@mail.com");
        assertThatThrownBy(() -> AccountOpeningService.open(holder(true, LocalDate.of(1990, 5, 14)), false,
                "ana.mail.com", NOW, "recepcion")).isInstanceOf(IllegalArgumentException.class);
        assertThat(AccountOpeningService.open(holder(true, LocalDate.of(1990, 5, 14)), false,
                "a".repeat(245) + "@mail.com", NOW, "recepcion").account().email()).hasSize(254);
        assertThatThrownBy(() -> AccountOpeningService.open(holder(true, LocalDate.of(1990, 5, 14)), false,
                "a".repeat(246) + "@mail.com", NOW, "recepcion")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Reason registeredRefusal(AccountHolder holder, boolean alreadyOpened) {
        try {
            AccountOpeningService.openRegistered(holder, alreadyOpened, "ana@mail.com", "$2a$hash", NOW);
        } catch (AccountRefusedException e) {
            return e.reason();
        }
        throw new AssertionError("It was opened");
    }

    /** The link proved the email is theirs, and they chose the password on registering (D-91). */
    @Test
    void anAdultWhoConfirmsTheirRegistrationOnTheWebGetsAnAccountInUseUnverified() {
        AccountUpdate opened = AccountOpeningService.openRegistered(
                new AccountHolder(PATIENT, "Ana Pérez", false, LocalDate.of(1990, 5, 14), true), false,
                "ana@mail.com", "$2a$hash", NOW);

        assertThat(opened.account().patientId()).isEqualTo(PATIENT);
        assertThat(opened.account().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(opened.account().passwordHash()).isEqualTo("$2a$hash");
        assertThat(opened.account().email()).isEqualTo("ana@mail.com");
        assertThat(opened.account().openedAt()).isEqualTo(NOW);
        assertThat(opened.change().action()).isEqualTo(AccountAction.REGISTERED);
        assertThat(opened.change().accountId()).isEqualTo(opened.account().id());
        assertThat(opened.change().by()).isNull();
        assertThat(opened.change().at()).isEqualTo(NOW);
    }

    @Test
    void registeringOnTheWebIsForAdultsOfEighteenOnTheDayWithoutAnAccount() {
        assertThat(AccountOpeningService.openRegistered(holder(false, LocalDate.of(2008, 3, 10)), false,
                "ana@mail.com", "$2a$hash", NOW).account().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(registeredRefusal(holder(false, LocalDate.of(2008, 3, 11)), false)).isEqualTo(Reason.UNDER_EIGHTEEN);
        assertThat(registeredRefusal(holder(false, null), false)).isEqualTo(Reason.UNDER_EIGHTEEN);
        assertThat(registeredRefusal(holder(false, LocalDate.of(1990, 5, 14)), true)).isEqualTo(Reason.ALREADY_OPENED);
    }
}
