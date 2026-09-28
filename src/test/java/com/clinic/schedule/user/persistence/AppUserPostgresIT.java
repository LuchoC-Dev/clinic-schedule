package com.clinic.schedule.user.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.clinic.schedule.user.api.Role;
import com.clinic.schedule.user.entity.AppUser;

/**
 * What only PostgreSQL can answer (D-44): that the database itself keeps a practitioner to one
 * active user, when two admins pass the application's check at once (D-68). The users are saved
 * straight through the repository, past that check, which is what the race amounts to.
 *
 * <p>Skipped when Docker is not running, so the build never depends on it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AppUserPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 10, 0);

    @Autowired
    private AppUserRepository users;

    private final UUID practitioner = UUID.randomUUID();

    @Test
    void aPractitionerCannotHaveTwoActiveUsers() {
        users.saveAndFlush(AppUser.create(fresh(), "hash", Role.PRACTITIONER, practitioner));

        assertThatThrownBy(() -> users.saveAndFlush(AppUser.create(fresh(), "hash", Role.PRACTITIONER, practitioner)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aDeactivatedUserLeavesThePractitionerFreeForAnother() {
        AppUser first = AppUser.create(fresh(), "hash", Role.PRACTITIONER, practitioner);
        first.deactivate(NOW, "admin");
        users.saveAndFlush(first);

        assertThatNoException().isThrownBy(
                () -> users.saveAndFlush(AppUser.create(fresh(), "hash", Role.PRACTITIONER, practitioner)));
    }

    @Test
    void theSeededAdminIsThere() {
        assertThat(users.findByUsernameAndActiveTrue("admin")).map(AppUser::getRole).contains(Role.ADMIN);
    }

    private static String fresh() {
        return "pg-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
