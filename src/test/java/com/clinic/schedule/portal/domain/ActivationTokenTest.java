package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The link that activates an account: unguessable, kept only as its hash, and good until it expires (D-79). */
class ActivationTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);

    @Test
    void onlyTheHashOfTheTokenIsKept() {
        UUID account = UUID.randomUUID();

        ActivationToken.Issued issued = ActivationToken.issue(account, NOW, Duration.ofHours(72));

        assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.stored().accountId()).isEqualTo(account);
        assertThat(issued.stored().hash()).isEqualTo(ActivationToken.hashOf(issued.token()))
                .isNotEqualTo(issued.token())
                .matches("[0-9a-f]{64}");
        assertThat(ActivationToken.issue(account, NOW, Duration.ofHours(72)).token()).isNotEqualTo(issued.token());
    }

    @Test
    void itWorksUntilItExpires() {
        ActivationToken stored = ActivationToken.issue(UUID.randomUUID(), NOW, Duration.ofHours(72)).stored();

        assertThat(stored.expiresAt()).isEqualTo(NOW.plusHours(72));
        assertThat(stored.isExpiredAt(NOW.plusHours(72).minusSeconds(1))).isFalse();
        assertThat(stored.isExpiredAt(NOW.plusHours(72))).isTrue();
    }
}
