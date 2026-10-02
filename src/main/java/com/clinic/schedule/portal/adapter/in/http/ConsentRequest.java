package com.clinic.schedule.portal.adapter.in.http;

/**
 * Whether the patient agrees to be reminded on each channel (D-81): true is their own consent, false
 * withdraws any — a responsible person's too —, and one left out is left as it is.
 */
record ConsentRequest(Boolean phone, Boolean email) {
}
