package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;
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
        return WebRegistrationPolicy.request(registrant, List.of(), email, "$2a$hash", NOW, VALID_FOR);
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

    private static RegisteredChild child(String number, LocalDate birthDate) {
        return new RegisteredChild(new Registrant("Tomás Pérez", "DNI", number, birthDate, null),
                FamilyMember.Relation.MOTHER);
    }

    private static final Registrant ADULT = born(LocalDate.of(1990, 5, 14));

    /** The children registered with the adult: under 13 on the day, each with what the adult is to them (D-92). */
    @Test
    void childrenAreUnderThirteenOnTheDayTheyAreRegistered() {
        WebRegistration.Issued issued = WebRegistrationPolicy.request(ADULT,
                List.of(child("50111222", LocalDate.of(2013, 3, 11)), child("50111333", LocalDate.of(2020, 1, 1))),
                "ana@mail.com", "$2a$hash", NOW, VALID_FOR);
        assertThat(issued.stored().children()).extracting(c -> c.person().documentNumber())
                .containsExactly("50111222", "50111333");

        assertThatThrownBy(() -> WebRegistrationPolicy.check(ADULT, List.of(child("50111222",
                LocalDate.of(2013, 3, 10))), NOW.toLocalDate()))
                .isInstanceOfSatisfying(FamilyRefusedException.class,
                        e -> assertThat(e.reason()).isEqualTo(FamilyRefusedException.Reason.NOT_A_CHILD));
        assertThatIllegalArgumentException().isThrownBy(() -> WebRegistrationPolicy.check(ADULT,
                List.of(child("50111222", null)), NOW.toLocalDate()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RegisteredChild(
                new Registrant("Tomás Pérez", "DNI", "50111222", LocalDate.of(2020, 1, 1), null), null));
    }

    @Test
    void upToFiveChildrenEachDocumentOnce() {
        LocalDate born = LocalDate.of(2020, 1, 1);
        List<RegisteredChild> five = List.of(child("50000001", born), child("50000002", born),
                child("50000003", born), child("50000004", born), child("50000005", born));
        WebRegistrationPolicy.check(ADULT, five, NOW.toLocalDate());
        List<RegisteredChild> six = new java.util.ArrayList<>(five);
        six.add(child("50000006", born));
        assertThatIllegalArgumentException().isThrownBy(() -> WebRegistrationPolicy.check(ADULT, six,
                NOW.toLocalDate()));

        // The same document typed twice, with or without its dots, the adult's included.
        assertThatIllegalArgumentException().isThrownBy(() -> WebRegistrationPolicy.check(ADULT,
                List.of(child("50.111.222", born), child("50111222", born)), NOW.toLocalDate()));
        assertThatIllegalArgumentException().isThrownBy(() -> WebRegistrationPolicy.check(ADULT,
                List.of(child("30-111-222", born)), NOW.toLocalDate()));
        // Another kind of document with the same number is another document.
        WebRegistrationPolicy.check(ADULT, List.of(new RegisteredChild(
                new Registrant("Tomás Pérez", "PASSPORT", "30111222", born, null), FamilyMember.Relation.FATHER)),
                NOW.toLocalDate());
    }
}
