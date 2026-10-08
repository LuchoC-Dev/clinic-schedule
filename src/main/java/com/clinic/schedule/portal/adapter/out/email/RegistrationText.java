package com.clinic.schedule.portal.adapter.out.email;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.clinic.schedule.portal.domain.RegistrationMessage;
import com.clinic.schedule.portal.domain.RegistrationRefusedMessage;

/**
 * The words of the emails that answer a registration made on the web, in Spanish (D-13, D-91): the link to confirm
 * it, or, for a document the practice already knows, the request to come to reception. Both under the same subject,
 * and neither says whether a document is a patient's or has an account: anyone may have typed it with their own
 * address. The link's names the children it registers with the adult, and those it could not, with nothing more
 * than that (D-92).
 */
final class RegistrationText {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String practice;
    private final String address;
    private final String phone;
    private final String baseUrl;

    RegistrationText(String practice, String address, String phone, String baseUrl) {
        this.practice = practice;
        this.address = address;
        this.phone = phone;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    String subject() {
        return practice + " — Tu registro en la web";
    }

    /** The link the adult follows: the token alone identifies the registration, and nothing personal goes in it. */
    String link(RegistrationMessage message) {
        return baseUrl + "/portal/register/confirm?token=" + message.token();
    }

    String plain(RegistrationMessage message) {
        return practice + "\n\n"
                + "Hola, " + message.fullName() + ":\n\n"
                + "Para terminar tu registro y abrir tu cuenta, entrá a este link y confirmalo:\n\n"
                + link(message) + "\n\n"
                + "El link vence el " + DAY.format(message.expiresAt()) + " a las " + TIME.format(message.expiresAt())
                + ". Si venció, registrate de nuevo.\n\n"
                + children(message)
                + "Cuando vengas por primera vez, traé tu documento: en recepción verificamos tu identidad. Hasta "
                + "entonces podés tener un turno por delante reservado por la web.\n\n"
                + "Si no te registraste, no hagas nada: sin confirmarlo, el registro se borra.\n\n"
                + practice + " · " + address + " · " + phone + "\n";
    }

    private static String children(RegistrationMessage message) {
        String text = "";
        if (!message.children().isEmpty()) {
            text += "Junto con vos registramos a " + names(message.children()) + ": desde tu cuenta vas a poder ver, "
                    + "sacar y cancelar sus turnos, uno por delante cada uno hasta que los verifiquemos. Traé también "
                    + "sus documentos y lo que prueba que sos su madre, padre o tutor.\n\n";
        }
        if (message.notRegistered().size() == 1) {
            text += "A " + names(message.notRegistered()) + " no lo pudimos registrar con ese documento: acercate a "
                    + "recepción con su documento.\n\n";
        } else if (!message.notRegistered().isEmpty()) {
            text += "A " + names(message.notRegistered()) + " no los pudimos registrar con esos documentos: acercate "
                    + "a recepción con sus documentos.\n\n";
        }
        return text;
    }

    /** "Tomás", "Tomás y Sofía", "Tomás, Sofía y Juan". */
    static String names(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.get(names.size() - 1);
    }

    String plain(RegistrationRefusedMessage message) {
        return practice + "\n\n"
                + "Hola, " + message.fullName() + ":\n\n"
                + "Recibimos un registro en la web con este email, y no lo pudimos completar con ese documento. "
                + "Acercate a recepción con tu documento, o llamanos al " + phone + ", y lo resolvemos.\n\n"
                + "Si no te registraste, no hagas nada.\n\n"
                + practice + " · " + address + " · " + phone + "\n";
    }
}
