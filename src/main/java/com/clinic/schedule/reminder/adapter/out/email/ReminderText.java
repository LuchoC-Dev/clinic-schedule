package com.clinic.schedule.reminder.adapter.out.email;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.web.util.HtmlUtils;

import com.clinic.schedule.reminder.domain.Recipient;
import com.clinic.schedule.reminder.domain.ReminderMessage;

/**
 * A reminder put into words, in Spanish since the patient reads it (D-13, D-78): the practice's name, a
 * greeting, the day, the time and the practitioner, and how to cancel or change it. Never the reason for
 * the appointment, which may be clinical; nor the practitioner's speciality or room, which the application
 * does not know.
 *
 * <ul>
 *   <li>On the patient's own email it greets them and speaks of their appointment.</li>
 *   <li>On a responsible person's it greets that person and names whose appointment it is.</li>
 *   <li>A patient under an alias is named nowhere, and greeted with nothing that says their sex.</li>
 * </ul>
 */
final class ReminderText {

    private static final Locale SPANISH = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d/MM/yyyy", SPANISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", SPANISH);

    private final String practice;
    private final String address;
    private final String phone;

    ReminderText(String practice, String address, String phone) {
        this.practice = practice;
        this.address = address;
        this.phone = phone;
    }

    String subject() {
        return "Recordatorio de turno — " + practice;
    }

    String plain(ReminderMessage message) {
        return practice + "\n\n" + greeting(message.recipient()) + "\n\n" + reminder(message) + "\n\n" + howToCancel()
                + "\n";
    }

    String html(ReminderMessage message) {
        return "<div style=\"font-family: sans-serif\">"
                + "<h2>" + escape(practice) + "</h2>"
                + "<p>" + escape(greeting(message.recipient())) + "</p>"
                + "<p>" + escape(reminder(message)) + "</p>"
                + "<p>" + escape(howToCancel()) + "</p>"
                + "</div>";
    }

    private static String greeting(Recipient recipient) {
        return recipient.greeted() == null ? "Hola:" : "Estimado/a " + recipient.greeted() + ":";
    }

    private static String reminder(ReminderMessage message) {
        Recipient recipient = message.recipient();
        String whose = !recipient.onBehalf() ? "su turno"
                : recipient.patientName() == null ? "un turno"
                : "el turno de " + recipient.patientName();
        return "Le recordamos " + whose + " del " + DAY.format(message.startsAt()) + " a las "
                + TIME.format(message.startsAt())
                + (message.practitioner() == null ? "" : " con " + message.practitioner()) + ".";
    }

    private String howToCancel() {
        return "Para cancelarlo o cambiarlo, llame al " + phone + " o acérquese a recepción, en " + address + ".";
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
