package com.clinic.schedule.portal.adapter.out.email;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.clinic.schedule.portal.domain.BookingConfirmation;

/**
 * The words of the email that confirms an appointment booked on the web, or moved there (D-93), in Spanish
 * (D-13, D-82): the practice, a greeting, whom it is for when not the account's own (D-85), the day, the time
 * — the new one, for a move — and the practitioner, the link to cancel it (D-83) and how to change it. Never the
 * reason, which may be clinical.
 */
final class BookingConfirmationText {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String practice;
    private final String address;
    private final String phone;
    private final String baseUrl;

    BookingConfirmationText(String practice, String address, String phone, String baseUrl) {
        this.practice = practice;
        this.address = address;
        this.phone = phone;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    String subject(BookingConfirmation confirmation) {
        return practice + (confirmation.moved() ? " — Turno cambiado" : " — Turno reservado");
    }

    /** The link when there is time to cancel on the web; a call otherwise, and to change it in any case. */
    private String howToCancel(BookingConfirmation confirmation) {
        if (confirmation.cancelToken() == null) {
            return "Para cancelarlo o cambiarlo, llamá al " + phone + " o acercate a recepción.\n\n";
        }
        return "Para cancelarlo, entrá a este link:\n\n" + baseUrl + "/portal/cancel?token="
                + confirmation.cancelToken() + "\n\nPara cambiarlo, llamá al " + phone
                + " o acercate a recepción.\n\n";
    }

    String plain(BookingConfirmation confirmation) {
        return practice + "\n\n"
                + "Hola, " + confirmation.fullName() + ":\n\n"
                + (confirmation.member() == null ? "Tu turno" : "El turno de " + confirmation.member())
                + (confirmation.moved() ? " se cambió al " : " quedó reservado para el ")
                + DAY.format(confirmation.startsAt()) + " a las "
                + TIME.format(confirmation.startsAt()) + " con " + confirmation.practitioner() + ".\n\n"
                + howToCancel(confirmation)
                + practice + " · " + address + " · " + phone + "\n";
    }
}
