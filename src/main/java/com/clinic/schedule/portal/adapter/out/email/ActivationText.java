package com.clinic.schedule.portal.adapter.out.email;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.clinic.schedule.portal.domain.ActivationMessage;

/**
 * The words of the activation email, in Spanish, since the patient reads them (D-13): the practice, a
 * greeting, what the account is for, the link and when it expires. Plain text: the link is all it carries,
 * and every mail reader makes it one to click.
 */
final class ActivationText {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String practice;
    private final String address;
    private final String phone;
    private final String baseUrl;

    ActivationText(String practice, String address, String phone, String baseUrl) {
        this.practice = practice;
        this.address = address;
        this.phone = phone;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    String subject() {
        return practice + " — Activá tu cuenta";
    }

    /** The link the patient follows: the token alone identifies the account, and nothing personal goes in it. */
    String link(ActivationMessage message) {
        return baseUrl + "/portal/activation?token=" + message.token();
    }

    String plain(ActivationMessage message) {
        return practice + "\n\n"
                + "Hola, " + message.fullName() + ":\n\n"
                + "En recepción abrimos tu cuenta para ver tus turnos por la web. Para activarla y elegir tu "
                + "contraseña, entrá a este link:\n\n"
                + link(message) + "\n\n"
                + "El link vence el " + DAY.format(message.expiresAt()) + " a las " + TIME.format(message.expiresAt())
                + ". Si venció, pedí otro en recepción.\n\n"
                + "Si no pediste esta cuenta, avisanos al " + phone + ".\n\n"
                + practice + " · " + address + " · " + phone + "\n";
    }
}
