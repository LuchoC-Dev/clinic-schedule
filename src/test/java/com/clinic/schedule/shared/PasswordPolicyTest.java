package com.clinic.schedule.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** From 10 to 72 characters, with nothing asked about what they are (D-68). */
class PasswordPolicyTest {

    @Test
    void tenCharactersAreEnoughWhateverTheyAre() {
        assertThat(PasswordPolicy.check("aaaaaaaaaa")).isEqualTo("aaaaaaaaaa");
    }

    @Test
    void fewerThanTenAreRefused() {
        assertThatThrownBy(() -> PasswordPolicy.check("aaaaaaaaa")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordPolicy.check(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seventyTwoBytesAreTheMostBecauseBcryptReadsNoFurther() {
        assertThat(PasswordPolicy.check("a".repeat(72))).hasSize(72);
        assertThatThrownBy(() -> PasswordPolicy.check("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
    }

    /** "ñ" is two bytes: 37 of them reach 74, past what BCrypt reads, and are refused rather than cut short. */
    @Test
    void accentedLettersCountForTheBytesTheyTake() {
        assertThat(PasswordPolicy.check("ñ".repeat(36))).hasSize(36);
        assertThatThrownBy(() -> PasswordPolicy.check("ñ".repeat(37))).isInstanceOf(IllegalArgumentException.class);
    }
}
