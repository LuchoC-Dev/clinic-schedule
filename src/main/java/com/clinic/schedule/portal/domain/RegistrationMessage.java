package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The email that lets an adult confirm the registration they made on the web (D-91): to the address they gave,
 * naming them as they typed it, with the token for the link and when it expires. How the link is written is the
 * sender's.
 *
 * @param children the children under 13 it registers with them, by name (D-92)
 * @param notRegistered the children whose document the practice already knew, by name: they are registered at
 *        reception
 */
public record RegistrationMessage(String address, String fullName, String token, LocalDateTime expiresAt,
        List<String> children, List<String> notRegistered) {

    public RegistrationMessage {
        children = List.copyOf(children);
        notRegistered = List.copyOf(notRegistered);
    }
}
