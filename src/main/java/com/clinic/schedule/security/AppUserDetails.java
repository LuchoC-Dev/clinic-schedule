package com.clinic.schedule.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user as Spring Security holds it, carrying the one thing the application needs
 * beyond the role: which practitioner the user stands for. Kept in the session, so it is not
 * read from the database on every request.
 */
class AppUserDetails implements UserDetails {

    private final String username;
    private final String passwordHash;
    private final Role role;
    private final UUID practitionerId;

    AppUserDetails(AppUser user) {
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.practitionerId = user.getPractitionerId();
    }

    UUID practitionerId() {
        return practitionerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
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
