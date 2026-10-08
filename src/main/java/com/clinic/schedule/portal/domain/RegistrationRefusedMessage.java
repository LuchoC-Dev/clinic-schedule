package com.clinic.schedule.portal.domain;

/**
 * The email that answers a registration made on the web with a document the practice already knows (D-91): it
 * opens nothing, and asks them to come to reception. It says neither whether that document is a patient's nor
 * whether it has an account, since anyone may have typed it with their own address.
 */
public record RegistrationRefusedMessage(String address, String fullName) {
}
