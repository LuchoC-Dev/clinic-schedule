package com.clinic.schedule.portal.domain;

/**
 * A patient's contact and health insurance as they want them to be (D-81): one left blank is cleared. A
 * phone's or an email's owner is a name and a relation such as "MOTHER", both blank when it is their own.
 */
public record OwnContact(String phoneNumber, String phoneOwnerName, String phoneOwnerRelation, String emailAddress,
        String emailOwnerName, String emailOwnerRelation, String insurer, String memberNumber, String plan) {
}
