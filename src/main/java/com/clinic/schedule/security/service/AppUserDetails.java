package com.clinic.schedule.security.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.clinic.schedule.user.api.Role;
import com.clinic.schedule.user.api.UserAccount;

/**
 * The logged-in user as Spring Security holds it, carrying the one thing the application needs
 * beyond the role: which practitioner the user stands for. Kept in the session, so it is not
 * read from the database on every request.
 *
 * <p>A user whose password is temporary carries {@link #TEMPORARY_PASSWORD} besides their role,
 * which keeps them on the password change until they make it (D-69).
 */
public class AppUserDetails implements UserDetails {

    /** The authority of a user whose password an admin set, and who must change it first. */
    public static final String TEMPORARY_PASSWORD = "TEMPORARY_PASSWORD";

    private final String username;
    private final String passwordHash;
    private final Role role;
    private final UUID practitionerId;
    private final boolean mustChangePassword;

    AppUserDetails(UserAccount account) {
        this.username = account.username();
        this.passwordHash = account.passwordHash();
        this.role = account.role();
        this.practitionerId = account.practitionerId();
        this.mustChangePassword = account.mustChangePassword();
    }

    /** The authority Spring Security checks: the role with its conventional prefix. */
    static String authorityOf(Role role) {
        return "ROLE_" + role.name();
    }

    UUID practitionerId() {
        return practitionerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        SimpleGrantedAuthority role = new SimpleGrantedAuthority(authorityOf(this.role));
        return mustChangePassword ? List.of(role, new SimpleGrantedAuthority(TEMPORARY_PASSWORD)) : List.of(role);
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
