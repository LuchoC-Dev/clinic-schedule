package com.clinic.schedule.user.presentation.http;

import java.util.UUID;

import com.clinic.schedule.user.api.Role;
import com.clinic.schedule.user.entity.AppUser;

/** A user as the API returns it: never their password, not even hashed. */
record UserResponse(UUID id, String username, Role role, UUID practitionerId, boolean active) {

    static UserResponse of(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getPractitionerId(),
                user.isActive());
    }
}
