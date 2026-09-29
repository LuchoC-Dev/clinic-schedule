package com.clinic.schedule.user.presentation.screen;

import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.user.api.Role;
import com.clinic.schedule.user.entity.AppUser;

/** A user as the admins' screens show them, in Spanish (D-13). */
record UserEntry(UUID id, String username, String role, String practitioner, boolean active) {

    static UserEntry of(AppUser user, Map<UUID, String> practitionerNames) {
        String practitioner = user.getPractitionerId() == null ? null
                : practitionerNames.getOrDefault(user.getPractitionerId(), "Profesional desconocido");
        return new UserEntry(user.getId(), user.getUsername(), role(user.getRole()), practitioner, user.isActive());
    }

    static String role(Role role) {
        return switch (role) {
            case RECEPTION -> "Recepción";
            case PRACTITIONER -> "Profesional";
            case ADMIN -> "Administración";
            case WAITING_ROOM -> "Sala de espera";
        };
    }
}
