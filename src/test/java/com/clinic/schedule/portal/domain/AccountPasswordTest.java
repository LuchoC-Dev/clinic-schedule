package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.AccountStateException;

/**
 * A web account's password after activating it (D-86): failed logins in a row lock it for a while, a link lets
 * the patient choose a new one, and they change it themselves; all recorded with nobody at reception.
 */
class AccountPasswordTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final Duration LOCK = Duration.ofMinutes(15);

    private static PatientAccount pending() {
        return AccountOpeningService.open(new AccountHolder(UUID.randomUUID(), "Ana Pérez", true,
                LocalDate.of(1990, 5, 14)), false, "ana@mail.com", NOW.minusDays(1), "recepcion").account();
    }

    private static PatientAccount active() {
        return pending().activate("$2a$10$hash", NOW).account();
    }

    private static PatientAccount failed(PatientAccount account, int times, LocalDateTime at) {
        for (int i = 0; i < times; i++) {
            account = account.loginFailed(at, 5, LOCK).account();
        }
        return account;
    }

    @Test
    void theFifthFailedLoginInARowLocksItForAWhileRecorded() {
        PatientAccount four = failed(active(), 4, NOW);
        assertThat(four.failedLogins()).isEqualTo(4);
        assertThat(four.isLockedAt(NOW)).isFalse();

        FailedLogin fifth = four.loginFailed(NOW, 5, LOCK);

        assertThat(fifth.account().isLockedAt(NOW)).isTrue();
        assertThat(fifth.account().lockedUntil()).isEqualTo(NOW.plusMinutes(15));
        assertThat(fifth.account().isLockedAt(NOW.plusMinutes(15).minusSeconds(1))).isTrue();
        assertThat(fifth.account().isLockedAt(NOW.plusMinutes(15))).isFalse();
        assertThat(fifth.account().failedLogins()).isZero();
        assertThat(fifth.locked().action()).isEqualTo(AccountAction.LOCKED);
        assertThat(fifth.locked().accountId()).isEqualTo(four.id());
        assertThat(fifth.locked().at()).isEqualTo(NOW);
        assertThat(fifth.locked().by()).isNull();
        assertThat(four.loginFailed(NOW, 6, LOCK).locked()).isNull();
    }

    @Test
    void tryingWhileLockedNeitherCountsNorLengthensIt() {
        PatientAccount locked = failed(active(), 5, NOW);

        FailedLogin again = locked.loginFailed(NOW.plusMinutes(10), 5, LOCK);

        assertThat(again.account()).isSameAs(locked);
        assertThat(again.locked()).isNull();
        // Once it is over, the count starts again: four more do not lock it.
        PatientAccount after = failed(locked, 4, NOW.plusMinutes(15));
        assertThat(after.isLockedAt(NOW.plusMinutes(15))).isFalse();
        assertThat(after.failedLogins()).isEqualTo(4);
        assertThat(after.lockedUntil()).isNull();
    }

    @Test
    void aLoginThatGoesThroughStartsTheCountAgain() {
        assertThat(active().loginSucceeded()).isEmpty();
        PatientAccount cleared = failed(active(), 3, NOW).loginSucceeded().orElseThrow();
        assertThat(cleared.failedLogins()).isZero();
        assertThat(failed(cleared, 4, NOW).isLockedAt(NOW)).isFalse();
        PatientAccount afterLock = failed(active(), 5, NOW).loginSucceeded().orElseThrow();
        assertThat(afterLock.lockedUntil()).isNull();
    }

    @Test
    void aNewPasswordFromTheLinkLiftsTheLock() {
        PatientAccount locked = failed(active(), 5, NOW);

        AccountUpdate reset = locked.resetPassword("$2a$10$new", NOW);

        assertThat(reset.account().passwordHash()).isEqualTo("$2a$10$new");
        assertThat(reset.account().isLockedAt(NOW)).isFalse();
        assertThat(reset.account().failedLogins()).isZero();
        assertThat(reset.change().action()).isEqualTo(AccountAction.PASSWORD_RESET);
        assertThat(reset.change().by()).isNull();
        assertThat(locked.passwordHash()).isEqualTo("$2a$10$hash");
    }

    @Test
    void thePatientChangesItThemselvesLeavingTheCountAsItWas() {
        PatientAccount two = failed(active(), 2, NOW);

        AccountUpdate changed = two.changePassword("$2a$10$new", NOW);

        assertThat(changed.account().passwordHash()).isEqualTo("$2a$10$new");
        assertThat(changed.account().failedLogins()).isEqualTo(2);
        assertThat(changed.change().action()).isEqualTo(AccountAction.PASSWORD_CHANGED);
        assertThat(changed.change().by()).isNull();
        PatientAccount locked = failed(active(), 5, NOW);
        assertThat(locked.changePassword("$2a$10$new", NOW).account().isLockedAt(NOW)).isTrue();
    }

    @Test
    void onlyAnAccountInUseRecoversOrChangesItsPassword() {
        AccountChange sent = active().recoverySent(NOW);
        assertThat(sent.action()).isEqualTo(AccountAction.RECOVERY_SENT);
        assertThat(sent.by()).isNull();

        PatientAccount inactive = active().deactivate(NOW, "recepcion").account();
        for (PatientAccount account : new PatientAccount[] {pending(), inactive}) {
            assertThatThrownBy(() -> account.recoverySent(NOW)).isInstanceOf(AccountStateException.class);
            assertThatThrownBy(() -> account.resetPassword("$2a$10$new", NOW))
                    .isInstanceOf(AccountStateException.class);
            assertThatThrownBy(() -> account.changePassword("$2a$10$new", NOW))
                    .isInstanceOf(AccountStateException.class);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> active().resetPassword(" ", NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> active().changePassword(null, NOW));
    }

    @Test
    void whatThePatientOrTheWebDoesHasNobodyAtReception() {
        UUID account = UUID.randomUUID();
        for (AccountAction action : new AccountAction[] {AccountAction.RECOVERY_SENT, AccountAction.PASSWORD_RESET,
                AccountAction.PASSWORD_CHANGED, AccountAction.LOCKED}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new AccountChange(UUID.randomUUID(), account,
                    action, NOW, "recepcion"));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountChange(UUID.randomUUID(), account,
                AccountAction.EMAIL_CHANGED, NOW, null));
        assertThatIllegalArgumentException().isThrownBy(() -> PatientAccount.restore(account, UUID.randomUUID(),
                "ana@mail.com", "$2a$10$hash", AccountStatus.ACTIVE, NOW, -1, null));
    }

    @Test
    void theLinkWorksForAnHourAndAnotherIsNotSentWithinFiveMinutes() {
        PasswordResetToken.Issued issued = PasswordResetToken.issue(UUID.randomUUID(), NOW, Duration.ofHours(1));
        PasswordResetToken stored = issued.stored();

        assertThat(stored.hash()).isEqualTo(SecretToken.hashOf(issued.token())).isNotEqualTo(issued.token());
        assertThat(PasswordResetToken.hashOf(issued.token())).isEqualTo(stored.hash());
        assertThat(stored.issuedAt()).isEqualTo(NOW);
        assertThat(stored.isExpiredAt(NOW.plusMinutes(59))).isFalse();
        assertThat(stored.isExpiredAt(NOW.plusHours(1))).isTrue();
        assertThat(stored.isRecentAt(NOW.plusMinutes(4), Duration.ofMinutes(5))).isTrue();
        assertThat(stored.isRecentAt(NOW.plusMinutes(5), Duration.ofMinutes(5))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new PasswordResetToken(UUID.randomUUID(), "hash",
                null, NOW));
    }
}
