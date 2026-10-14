package com.clinic.schedule.security.presentation.http;

import java.util.List;

/**
 * Whether a patient is logged in to the patients' application, the CSRF token its writes carry, and the
 * types of document its login offers (D-80, D-97). Nothing of the patient: the principal is their account,
 * and what a patient reads is asked for by the screen that shows it.
 */
record PatientSessionResponse(boolean loggedIn, String csrfToken, List<String> documentTypes) {
}
