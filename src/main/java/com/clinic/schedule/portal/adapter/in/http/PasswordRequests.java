package com.clinic.schedule.portal.adapter.in.http;

/** The bodies a patient sends about their password (D-86). */
final class PasswordRequests {

    private PasswordRequests() {
    }

    /** Asking for the link, with the document as the login takes it. */
    record Recovery(String documentType, String documentNumber) {
    }

    /** The token of the email's link, and the new password. */
    record Reset(String token, String password) {
    }

    /** Logged in: the current password, and the new one. */
    record Change(String currentPassword, String newPassword) {
    }
}
