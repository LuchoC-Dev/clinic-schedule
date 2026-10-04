package com.clinic.schedule.portal.adapter.out.email;

import java.time.format.DateTimeFormatter;

import com.clinic.schedule.portal.domain.PasswordResetMessage;

/**
 * The words of the email with the link to choose a new password, in Spanish (D-13, D-86): the practice, a
 * greeting, the link, when it expires, and what to do if the patient did not ask for it.
 */
final class PasswordResetText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String practice;
    private final String address;
    private final String phone;
    private final String baseUrl;

    PasswordResetText(String practice, String address, String phone, String baseUrl) {
        this.practice = practice;
        this.address = address;
        this.phone = phone;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    String subject() {
        return practice + " — Elegí una contraseña nueva";
    }

    String plain(PasswordResetMessage message) {
        return practice + "\n\n"
                + "Hola, " + message.fullName() + ":\n\n"
                + "Para elegir una contraseña nueva para tu cuenta, entrá a este link:\n\n"
                + baseUrl + "/portal/password-reset?token=" + message.token() + "\n\n"
                + "El link vale hasta las " + TIME.format(message.expiresAt()) + " y sirve una sola vez. Si venció, "
                + "pedí otro desde la página para ingresar.\n\n"
                + "Si no lo pediste, no hagas nada: tu contraseña sigue siendo la misma.\n\n"
                + practice + " · " + address + " · " + phone + "\n";
    }
}
