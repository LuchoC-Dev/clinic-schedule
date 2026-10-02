package com.clinic.schedule.security.api;

import java.util.UUID;

/**
 * The name a patient logged in to their web account goes by wherever something records who did it (D-81):
 * {@code portal:} and the account's id — never their document, which would say who the patient is to anyone
 * reading the record (D-80). A staff username cannot hold a colon (D-68), so the two are never confused, and
 * a screen tells a patient's doing by it.
 */
public final class PatientUsername {

    private static final String PREFIX = "portal:";

    private PatientUsername() {
    }

    public static String of(UUID accountId) {
        return PREFIX + accountId;
    }

    /** Whether a recorded username is a patient's web account. */
    public static boolean is(String username) {
        return username != null && username.startsWith(PREFIX);
    }
}
