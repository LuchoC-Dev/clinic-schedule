package com.clinic.schedule.patient.presentation.http;

import com.clinic.schedule.patient.entity.Consent;

/**
 * Who agreed to the patient being reminded on each channel, as it is to be (D-77): {@code PATIENT},
 * {@code OWNER} for the responsible person a channel belongs to, or null — or left out — for nobody,
 * which withdraws it.
 */
record ContactConsentRequest(Consent phone, Consent email) {
}
