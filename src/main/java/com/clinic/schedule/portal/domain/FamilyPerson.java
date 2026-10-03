package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A patient as a family group's rules need them (D-84): who, and when they were born.
 *
 * @param birthDate null when nobody recorded it
 */
public record FamilyPerson(UUID patientId, LocalDate birthDate) {
}
