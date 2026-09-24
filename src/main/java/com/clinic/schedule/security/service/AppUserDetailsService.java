package com.clinic.schedule.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.security.persistence.AppUserRepository;

/** Where Spring Security looks users up when they log in. */
@Service
class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsernameAndActiveTrue(username)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown or inactive user"));
    }
}
