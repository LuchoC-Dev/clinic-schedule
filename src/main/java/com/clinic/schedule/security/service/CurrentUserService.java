package com.clinic.schedule.security.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.clinic.schedule.security.api.CurrentUserReader;
import com.clinic.schedule.security.entity.Role;

/**
 * Answers {@link CurrentUserReader} from the security context of the current request.
 *
 * <p>It fails closed: a user with the practitioner role whose practitioner cannot be told is
 * refused, not treated as reception. That cannot happen with users loaded from the database,
 * but it would be the wrong way to be wrong.
 */
@Service
class CurrentUserService implements CurrentUserReader {

    @Override
    public Optional<UUID> practitionerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AppUserDetails user && user.practitionerId() != null) {
            return Optional.of(user.practitionerId());
        }
        boolean isPractitioner = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Role.PRACTITIONER.authority()::equals);
        if (isPractitioner) {
            throw new IllegalStateException("A practitioner user without a practitioner cannot be served");
        }
        return Optional.empty();
    }
}
