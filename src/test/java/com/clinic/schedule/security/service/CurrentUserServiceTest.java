package com.clinic.schedule.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.clinic.schedule.security.entity.Role;

/**
 * Who the current user stands for, and the case the database cannot produce: a user with the
 * practitioner role and no practitioner. It must be refused, never taken for reception (D-34).
 */
class CurrentUserServiceTest {

    private final CurrentUserService currentUser = new CurrentUserService();

    @AfterEach
    void clearTheContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loggedInAs(Role role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "someone", null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    @Test
    void receptionStandsForNoPractitioner() {
        loggedInAs(Role.RECEPTION);

        assertThat(currentUser.practitionerId()).isEmpty();
    }

    @Test
    void aPractitionerWhosePractitionerCannotBeToldIsRefused() {
        loggedInAs(Role.PRACTITIONER);

        assertThatThrownBy(currentUser::practitionerId).isInstanceOf(IllegalStateException.class);
    }
}
