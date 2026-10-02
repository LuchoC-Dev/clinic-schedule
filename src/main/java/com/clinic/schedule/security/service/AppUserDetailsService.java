package com.clinic.schedule.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.clinic.schedule.user.api.UserAccountReader;

/**
 * Where Spring Security looks the practice's users up when they log in: in the user module, through its api
 * (D-43). Patients are looked up elsewhere, on a login of their own (D-80); this is the one asked for when
 * nobody says which, as the tests' users are the staff.
 */
@Service
@Primary
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountReader users;

    AppUserDetailsService(UserAccountReader users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findActiveByUsername(username)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown or inactive user"));
    }
}
