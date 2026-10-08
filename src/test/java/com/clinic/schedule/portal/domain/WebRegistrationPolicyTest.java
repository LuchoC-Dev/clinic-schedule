package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.InvalidAccountEmailException;

/**
 * Who registers themselves on the web (D-91): 18 or older on the day, with an email that can be the account's;
 * and the registration, kept as the hash of its link's token, until it expires.
 */
class WebRegistrationPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final Duration VALID_FOR = Duration.ofHours(72);

    private static Registrant born(LocalDate birthDate) {
        return new Registrant("Ana Pérez", "DNI", "30111222", birthDate, null);
    }

    private static WebRegistration.Issued request(Registrant registrant, String email) {
        return WebRegistrationPolicy.request(registrant, email, "$2a$hash", NOW, VALID_FOR);
    }

    @Test
    void eighteenIsCountedOnTheDayTheyRegister() {
        assertThat(request(born(LocalDate.of(2008, 3, 10)), "ana@mail.com").stored().registrant().birthDate())
                .isEqualTo(LocalDate.of(2008, 3, 10));
        assertThatThrownBy(() -> request(born(LocalDate.of(2008, 3, 11)), "ana@mail.com"))
                .isInstanceOfSatisfying(AccountRefusedException.class,
                        e -> assertThat(e.reason()).isEqualTo(AccountRefusedException.Reason.UNDER_EIGHTEEN));
    }

    @Test
    void aDateOfBirthIsRequired() {
        assertThatIllegalArgumentException().isThrownBy(() -> request(born(null), "ana@mail.com"));
    }

    @Test
    void theEmailIsCheckedAndKeptAsTheAccountKeepsIt() {
        assertThat(request(born(LocalDate.of(1990, 5, 14)), "  Ana@Mail.COM ").stored().email())
                .isEqualTo("ana@mail.com");
        assertThatThrownBy(() -> request(born(LocalDate.of(1990, 5, 14)), "ana-at-mail"))
                .isInstanceOf(InvalidAccountEmailException.class);
    }

    /** Kept as typed, separators and all, while it waits: no longer than what keeps it. */
    @Test
    void aDocumentNumberOrAPhoneLongerThanFortyCharactersCannotBeOne() {
        LocalDate born = LocalDate.of(1990, 5, 14);
        assertThat(new Registrant("Ana Pérez", "DNI", "3".repeat(40), born, "1".repeat(40)).documentNumber())
                .hasSize(40);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Registrant("Ana Pérez", "DNI", "3".repeat(41), born, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Registrant("Ana Pérez", "DNI", "30111222", born, "1".repeat(41)));
    }

    @Test
    void onlyTheHashOfTheTokenIsKeptUntilItExpires() {
        WebRegistration.Issued issued = request(born(LocalDate.of(1990, 5, 14)), "ana@mail.com");
        WebRegistration stored = issued.stored();

        assertThat(issued.token()).hasSize(43);
        assertThat(stored.hash()).isEqualTo(WebRegistration.hashOf(issued.token())).isNotEqualTo(issued.token());
        assertThat(stored.passwordHash()).isEqualTo("$2a$hash");
        assertThat(stored.requestedAt()).isEqualTo(NOW);
        assertThat(stored.expiresAt()).isEqualTo(NOW.plusHours(72));
        assertThat(stored.isExpiredAt(NOW.plusHours(72).minusSeconds(1))).isFalse();
        assertThat(stored.isExpiredAt(NOW.plusHours(72))).isTrue();
        assertThat(request(born(LocalDate.of(1990, 5, 14)), "ana@mail.com").token()).isNotEqualTo(issued.token());
    }
}
