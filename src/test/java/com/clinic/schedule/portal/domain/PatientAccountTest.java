package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.AccountStateException;

/** What a web account goes through once opened (D-79), each step with the entry that records it. */
class PatientAccountTest {

    private static final LocalDateTime OPENED = LocalDateTime.of(2026, 3, 9, 9, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);

    private static PatientAccount pending() {
        return AccountOpeningService.open(new AccountHolder(UUID.randomUUID(), "Ana Pérez", true,
                LocalDate.of(1990, 5, 14)), false, "ana@mail.com", OPENED, "recepcion").account();
    }

    private static PatientAccount active() {
        return pending().activate("$2a$10$hash", NOW).account();
    }

    @Test
    void thePatientActivatesItWithTheirPasswordAndNobodyAtReceptionIsRecorded() {
        PatientAccount pending = pending();

        AccountUpdate activated = pending.activate("$2a$10$hash", NOW);

        assertThat(activated.account().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(activated.account().passwordHash()).isEqualTo("$2a$10$hash");
        assertThat(activated.change().action()).isEqualTo(AccountAction.ACTIVATED);
        assertThat(activated.change().by()).isNull();
        assertThat(pending.status()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void onlyAPendingAccountIsActivatedOrSentItsActivation() {
        assertThatThrownBy(() -> active().activate("$2a$10$other", NOW)).isInstanceOf(AccountStateException.class);
        assertThatThrownBy(() -> active().activationSent(NOW, "recepcion")).isInstanceOf(AccountStateException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> pending().activate(" ", NOW));

        AccountChange sent = pending().activationSent(NOW, "recepcion");
        assertThat(sent.action()).isEqualTo(AccountAction.ACTIVATION_SENT);
        assertThat(sent.by()).isEqualTo("recepcion");
    }

    @Test
    void theSameEmailChangesNothingAndAnotherOneIsRecorded() {
        PatientAccount account = active();

        assertThat(account.changeEmail(" ANA@mail.com", NOW, "recepcion")).isEmpty();
        AccountUpdate changed = account.changeEmail("ana.perez@mail.com", NOW, "recepcion").orElseThrow();
        assertThat(changed.account().email()).isEqualTo("ana.perez@mail.com");
        assertThat(changed.account().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(changed.change().action()).isEqualTo(AccountAction.EMAIL_CHANGED);
        assertThatIllegalArgumentException().isThrownBy(() -> account.changeEmail("ana", NOW, "recepcion"));
    }

    @Test
    void aDeactivatedAccountComesBackAsItWasLeft() {
        AccountUpdate deactivated = active().deactivate(NOW, "recepcion");
        assertThat(deactivated.account().status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(deactivated.change().action()).isEqualTo(AccountAction.DEACTIVATED);
        assertThatThrownBy(() -> deactivated.account().deactivate(NOW, "recepcion"))
                .isInstanceOf(AccountStateException.class);

        AccountUpdate reactivated = deactivated.account().reactivate(NOW, "recepcion");
        assertThat(reactivated.account().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reactivated.change().action()).isEqualTo(AccountAction.REACTIVATED);

        PatientAccount neverActivated = pending().deactivate(NOW, "recepcion").account();
        assertThat(neverActivated.reactivate(NOW, "recepcion").account().status()).isEqualTo(AccountStatus.PENDING);

        assertThatThrownBy(() -> active().reactivate(NOW, "recepcion")).isInstanceOf(AccountStateException.class);
    }

    @Test
    void aPendingAccountHasNoPasswordAndAnActiveOneHasOne() {
        UUID id = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> PatientAccount.restore(id, patient, "ana@mail.com",
                "$2a$10$hash", AccountStatus.PENDING, OPENED, 0, null));
        assertThatIllegalArgumentException().isThrownBy(() -> PatientAccount.restore(id, patient, "ana@mail.com",
                null, AccountStatus.ACTIVE, OPENED, 0, null));
    }

    @Test
    void receptionRecordsWhoDidItAndThePatientIsNobodyAtReception() {
        UUID account = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountChange(UUID.randomUUID(), account,
                AccountAction.ACTIVATED, NOW, "recepcion"));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountChange(UUID.randomUUID(), account,
                AccountAction.DEACTIVATED, NOW, null));
    }
}
