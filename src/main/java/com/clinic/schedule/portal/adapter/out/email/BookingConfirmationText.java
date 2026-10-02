package com.clinic.schedule.portal.adapter.out.email;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.clinic.schedule.portal.domain.BookingConfirmation;

/**
 * The words of the email that confirms an appointment booked on the web, in Spanish (D-13, D-82): the
 * practice, a greeting, the day, the time and the practitioner, and how to cancel or change it. Never the
 * reason, which may be clinical.
 */
final class BookingConfirmationText {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String practice;
    private final String address;
    private final String phone;

    BookingConfirmationText(String practice, String address, String phone) {
        this.practice = practice;
        this.address = address;
        this.phone = phone;
    }

    String subject() {
        return practice + " — Turno reservado";
    }

    String plain(BookingConfirmation confirmation) {
        return practice + "\n\n"
                + "Hola, " + confirmation.fullName() + ":\n\n"
                + "Tu turno quedó reservado para el " + DAY.format(confirmation.startsAt()) + " a las "
                + TIME.format(confirmation.startsAt()) + " con " + confirmation.practitioner() + ".\n\n"
                + "Para cancelarlo o cambiarlo, llamá al " + phone + " o acercate a recepción.\n\n"
                + practice + " · " + address + " · " + phone + "\n";
    }
}
