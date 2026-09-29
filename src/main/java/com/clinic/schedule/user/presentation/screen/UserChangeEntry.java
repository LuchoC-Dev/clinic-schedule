package com.clinic.schedule.user.presentation.screen;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.clinic.schedule.user.entity.AppUserChange;

/** What an admin did to a user, as the user's screen lists it (D-68). */
record UserChangeEntry(String when, String by, String action) {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    static UserChangeEntry of(AppUserChange change) {
        String action = switch (change.getAction()) {
            case CREATED -> "Creó el usuario";
            case DEACTIVATED -> "Lo desactivó";
            case REACTIVATED -> "Lo reactivó";
            case PASSWORD_RESET -> "Blanqueó la contraseña";
            case PASSWORD_CHANGED -> "Cambió su contraseña";
        };
        return new UserChangeEntry(WHEN.format(change.getChangedAt()), change.getChangedBy(), action);
    }
}
