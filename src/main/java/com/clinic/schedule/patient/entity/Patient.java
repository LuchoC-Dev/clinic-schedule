package com.clinic.schedule.patient.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 *
 * <p>Nobody is turned away for lack of an identity (D-60). A patient who gives no name is
 * registered under an alias, which stands as their name until they give one and stays theirs
 * afterwards, so a note that says NN-4821 still finds them. How far the practice knows who they
 * are is {@link IdentityStatus}, and every change to it goes through {@link #changeIdentity}, which
 * says what changed.
 */
@Entity
@Table(name = "patient")
public class Patient {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Null when the patient did not give one, which is allowed and common. */
    @Embedded
    private IdentityDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status", nullable = false, length = 10)
    private IdentityStatus identityStatus;

    /** Only for a patient registered without a name; kept once they give one. */
    @Column(length = 7)
    private String alias;

    protected Patient() {
        // required by JPA
    }

    private Patient(UUID id, String fullName, IdentityDocument document, IdentityStatus identityStatus,
            String alias) {
        this.id = id;
        this.fullName = requireName(fullName);
        this.document = document;
        this.identityStatus = identityStatus;
        this.alias = alias;
    }

    /**
     * A new patient who gave a name: verified when reception saw their document as it registered
     * them, declared otherwise. The id is assigned here and not by the database (D-19).
     *
     * @throws UnverifiableIdentityException when the document is said to be seen and there is none
     */
    public static Patient register(String fullName, IdentityDocument document, boolean documentSeen) {
        if (documentSeen && document == null) {
            throw new UnverifiableIdentityException("An identity is verified on a document, and there is none");
        }
        return new Patient(UUID.randomUUID(), fullName, document,
                documentSeen ? IdentityStatus.VERIFIED : IdentityStatus.DECLARED, null);
    }

    /** A new patient who gave no name, under an alias such as NN-4821, which stands as their name. */
    public static Patient registerUnidentified(String alias) {
        if (alias == null || !ALIAS_FORMAT.matcher(alias).matches()) {
            throw new IllegalArgumentException("An alias is NN- and four digits, such as NN-4821");
        }
        return new Patient(UUID.randomUUID(), alias, null, IdentityStatus.ALIAS, alias);
    }

    /**
     * Changes who the patient is, and says what changed, or nothing when nothing did.
     *
     * <ul>
     *   <li>While the name is the alias, the patient goes by their alias, and cannot be verified:
     *       a document is checked against a name.</li>
     *   <li>{@code documentSeen} verifies the identity, and needs a document.</li>
     *   <li>Otherwise a verified patient stays verified while their name and document stay as
     *       they were, and is only declared once either changes: nobody has seen the new one.</li>
     * </ul>
     *
     * @param document null clears it, which is how a wrongly entered one is taken back out
     * @param documentSeen whether reception saw the document now
     * @param changedBy the username of whoever changes it
     * @throws UnverifiableIdentityException when it is said to be seen and cannot be
     */
    public Optional<PatientIdentityChange> changeIdentity(String fullName, IdentityDocument document,
            boolean documentSeen, LocalDateTime changedAt, String changedBy) {
        String name = requireName(fullName);
        boolean byAlias = name.equals(alias);
        boolean changed = !name.equals(this.fullName) || !Objects.equals(document, this.document);
        IdentityStatus status;
        if (documentSeen) {
            if (document == null) {
                throw new UnverifiableIdentityException("An identity is verified on a document, and there is none");
            }
            if (byAlias) {
                throw new UnverifiableIdentityException("An identity is verified with the patient's name, not their alias");
            }
            status = IdentityStatus.VERIFIED;
        } else if (byAlias) {
            status = IdentityStatus.ALIAS;
        } else if (identityStatus == IdentityStatus.VERIFIED && !changed) {
            status = IdentityStatus.VERIFIED;
        } else {
            status = IdentityStatus.DECLARED;
        }
        if (!changed && status == identityStatus) {
            return Optional.empty();
        }
        PatientIdentityChange change = new PatientIdentityChange(id, changedAt, changedBy,
                this.fullName, this.document, this.identityStatus, name, document, status);
        this.fullName = name;
        this.document = document;
        this.identityStatus = status;
        return Optional.of(change);
    }

    /**
     * A name is required, fits its column, and has at least one letter: "!!!" or "123" is a
     * slip of the keyboard, not somebody's name (D-38). Nothing stricter — names carry
     * accents, apostrophes and hyphens.
     */
    private static String requireName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("A patient needs a full name");
        }
        String name = fullName.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A full name is at most " + MAX_NAME_LENGTH + " characters");
        }
        if (name.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("A full name needs at least one letter");
        }
        return name;
    }

    /** As long as the column that stores it. */
    static final int MAX_NAME_LENGTH = 150;

    private static final Pattern ALIAS_FORMAT = Pattern.compile("NN-\\d{4}");

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public IdentityDocument getDocument() {
        return document;
    }

    public IdentityStatus getIdentityStatus() {
        return identityStatus;
    }

    /** Null for a patient registered with a name. */
    public String getAlias() {
        return alias;
    }
}
