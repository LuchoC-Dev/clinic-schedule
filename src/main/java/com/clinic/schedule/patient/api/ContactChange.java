package com.clinic.schedule.patient.api;

/**
 * A patient's contact and health insurance as they are to be, in plain values (D-81): one left blank is
 * cleared. A phone's or an email's owner is a name and a relation such as "MOTHER", both blank when it is
 * the patient's own. The formats are checked by the patient module, as reception's are (D-38).
 */
public record ContactChange(String phoneNumber, String phoneOwnerName, String phoneOwnerRelation, String emailAddress,
        String emailOwnerName, String emailOwnerRelation, String insurer, String memberNumber, String plan) {
}
