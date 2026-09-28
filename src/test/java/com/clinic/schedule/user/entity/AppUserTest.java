package com.clinic.schedule.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.clinic.schedule.user.api.Role;

/** What a user guards on their own (D-68): a well-formed username, the practitioner of their role, and each change recorded. */
class AppUserTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final UUID ARIAS = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"ab", "Recepcion", "ana perez", "maría", "ana@clinic", ""})
    void aMalformedUsernameIsRefused(String username) {
        assertThatThrownBy(() -> AppUser.create(username, "hash", Role.RECEPTION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aUsernameIsAtMostFiftyCharacters() {
        assertThat(AppUser.create("a".repeat(50), "hash", Role.RECEPTION, null).getUsername()).hasSize(50);
        assertThatThrownBy(() -> AppUser.create("a".repeat(51), "hash", Role.RECEPTION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPractitionersUserNeedsTheirPractitioner() {
        assertThatThrownBy(() -> AppUser.create("arias2", "hash", Role.PRACTITIONER, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AppUser.create("arias2", "hash", Role.PRACTITIONER, ARIAS).getPractitionerId()).isEqualTo(ARIAS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTION", "ADMIN"})
    void onlyAPractitionersUserStandsForAPractitioner(Role role) {
        assertThatThrownBy(() -> AppUser.create("otro", "hash", role, ARIAS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aUserNeedsARoleAndAPassword() {
        assertThatThrownBy(() -> AppUser.create("otro", "hash", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AppUser.create("otro", " ", Role.RECEPTION, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNewUserIsActive() {
        AppUser user = AppUser.create("recepcion.b", "hash", Role.RECEPTION, null);

        assertThat(user.isActive()).isTrue();
        assertThat(user.created(NOW, "admin").getAction()).isEqualTo(UserAction.CREATED);
    }

    @Test
    void deactivatingAndReactivatingAreRecordedWithWhoAndWhen() {
        AppUser user = AppUser.create("recepcion.b", "hash", Role.RECEPTION, null);

        AppUserChange deactivated = user.deactivate(NOW, "admin");
        assertThat(user.isActive()).isFalse();
        assertThat(deactivated.getAction()).isEqualTo(UserAction.DEACTIVATED);
        assertThat(deactivated.getUserId()).isEqualTo(user.getId());
        assertThat(deactivated.getChangedBy()).isEqualTo("admin");
        assertThat(deactivated.getChangedAt()).isEqualTo(NOW);

        assertThat(user.reactivate(NOW.plusDays(1), "admin").getAction()).isEqualTo(UserAction.REACTIVATED);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void aUserIsNotDeactivatedTwiceNorReactivatedWhileActive() {
        AppUser user = AppUser.create("recepcion.b", "hash", Role.RECEPTION, null);

        assertThatThrownBy(() -> user.reactivate(NOW, "admin")).isInstanceOf(UserStatusException.class);
        user.deactivate(NOW, "admin");
        assertThatThrownBy(() -> user.deactivate(NOW, "admin")).isInstanceOf(UserStatusException.class);
    }

    @Test
    void resettingThePasswordReplacesItAndIsRecorded() {
        AppUser user = AppUser.create("recepcion.b", "old", Role.RECEPTION, null);

        assertThat(user.resetPassword("new", NOW, "admin").getAction()).isEqualTo(UserAction.PASSWORD_RESET);
        assertThat(user.getPasswordHash()).isEqualTo("new");
    }

    @Test
    void aChangeNeedsWhoMadeIt() {
        AppUser user = AppUser.create("recepcion.b", "hash", Role.RECEPTION, null);

        assertThatThrownBy(() -> user.deactivate(NOW, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
