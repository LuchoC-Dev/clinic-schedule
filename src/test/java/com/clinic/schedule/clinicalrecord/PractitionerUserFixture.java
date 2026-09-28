package com.clinic.schedule.clinicalrecord;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A user who stands for a practitioner the test created, to act as that practitioner.
 *
 * <p>The seeded practitioners are shared by every test, and a consultation needs a patient who
 * arrived today — a day other tests book on. So each test makes its own practitioner through the
 * endpoints, and here a user for them. The row is inserted directly, the way {@code SecurityIT}
 * does, rather than through the admins' endpoint: these tests are about the record, not the users.
 * The user is then loaded as the application would load it at login.
 */
public final class PractitionerUserFixture {

    private final JdbcTemplate jdbc;
    private final UserDetailsService users;

    public PractitionerUserFixture(JdbcTemplate jdbc, UserDetailsService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /** A request made by a new user standing for that practitioner. */
    public RequestPostProcessor userFor(UUID practitionerId) {
        String username = "profesional-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into app_user (id, username, password_hash, role, practitioner_id, active) "
                        + "values (?, ?, 'unused', 'PRACTITIONER', ?, true)",
                UUID.randomUUID(), username, practitionerId);
        return user(users.loadUserByUsername(username));
    }
}
