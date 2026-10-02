package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The patient a web account would be opened for, as much as opening one needs to know (D-79).
 *
 * @param birthDate null when nobody recorded it
 */
public record AccountHolder(UUID patientId, String fullName, boolean identityVerified, LocalDate birthDate) {
}
