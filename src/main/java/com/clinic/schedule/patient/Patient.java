package com.clinic.schedule.patient;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A patient, as much as booking an appointment needs: who they are and, when they gave it,
 * the document they identify themselves with.
 *
 * <p>JPA maps this class directly, with no separate row class: there is no invariant here
 * that an entity cannot hold, so the separation `appointment` needs would be ceremony
 * (D-25).
 *
 * <p>The full name is one field for now. Splitting it into given and family names is a
 * later decision, and nothing in v1 needs the parts.
 */
@Entity
@Table(name = "patient")
class Patient {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Null when the patient did not give one, which is allowed and common. */
    @Embedded
    private IdentityDocument document;

    protected Patient() {
        // required by JPA
    }

    private Patient(UUID id, String fullName, IdentityDocument document) {
        this.id = id;
        this.fullName = requireName(fullName);
        this.document = document;
    }

    /** A new patient. The id is assigned here and not by the database (D-19). */
    static Patient register(String fullName, IdentityDocument document) {
        return new Patient(UUID.randomUUID(), fullName, document);
    }

    void rename(String fullName) {
        this.fullName = requireName(fullName);
    }

    /** Null clears the document, which is how a wrongly entered one is taken back out. */
    void setDocument(IdentityDocument document) {
        this.document = document;
    }

    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("A patient needs a full name");
        }
        return fullName.strip();
    }

    UUID getId() {
        return id;
    }

    String getFullName() {
        return fullName;
    }

    IdentityDocument getDocument() {
        return document;
    }
}
