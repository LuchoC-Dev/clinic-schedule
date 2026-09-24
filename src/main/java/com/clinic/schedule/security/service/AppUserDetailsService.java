package com.clinic.schedule.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.clinic.schedule.user.api.UserAccountReader;

/** Where Spring Security looks users up when they log in: in the user module, through its api (D-43). */
@Service
class AppUserDetailsService implements UserDetailsService {

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
