package com.clinic.schedule.patient;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A patient, as much as booking an appointment needs: who they are and, when they gave it,
 * their national ID.
 *
 * <p>JPA maps this class directly, with no separate row class: there is no invariant here
 * that an entity cannot hold, so the separation `appointment` needs would be ceremony
 * (D-25).
 *
 * <p>What it does guard is its own shape — a name that is not blank, a national ID that is
 * either absent or meaningful — and it exposes no setters: {@link #rename} and
 * {@link #setNationalId} say what they do and normalize what they store.
 */
@Entity
@Table(name = "patient")
class Patient {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Null when the patient did not give one, which is allowed and common. */
    @Column(name = "national_id", length = 20)
    private String nationalId;

    protected Patient() {
        // required by JPA
    }

    private Patient(UUID id, String fullName, String nationalId) {
        this.id = id;
        this.fullName = requireName(fullName);
        this.nationalId = normalizeNationalId(nationalId);
    }

    /** A new patient. The id is assigned here and not by the database (D-19). */
    static Patient register(String fullName, String nationalId) {
        return new Patient(UUID.randomUUID(), fullName, nationalId);
    }

    void rename(String fullName) {
        this.fullName = requireName(fullName);
    }

    void setNationalId(String nationalId) {
        this.nationalId = normalizeNationalId(nationalId);
    }

    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("A patient needs a full name");
        }
        return fullName.strip();
    }

    /**
     * A blank national ID is stored as absent, so "" and "   " do not become a value.
     * Package-private so the service can normalize a value before comparing it with what is
     * already stored, without having to know the rule.
     */
    static String normalizeNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return null;
        }
        return nationalId.strip();
    }

    UUID getId() {
        return id;
    }

    String getFullName() {
        return fullName;
    }

    String getNationalId() {
        return nationalId;
    }
}
