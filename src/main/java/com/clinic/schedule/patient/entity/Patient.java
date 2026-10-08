package com.clinic.schedule.patient.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 *
 * <p>Besides who they are, the practice may know when they were born, how to reach them and their
 * health insurance — their {@link PatientDetails}, all of it optional, changed apart through
 * {@link #changeDetails} and kept in a history of their own (D-73, D-74). The phone, the email and the
 * insurance are plain columns rather than embeddables, the phone and the email each with the person
 * it belongs to beside it, and come back as value objects.
 *
 * <p>Each of the two channels carries its own consent to be reminded on it, with who gave it, changed
 * through {@link #changeConsent} and kept in the same history (D-77). It is not one of the details: it
 * was given for a number and a person, so changing either drops it.
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

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number", length = 16)
    private String phoneNumber;

    @Column(name = "phone_owner_name", length = 150)
    private String phoneOwnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "phone_owner_relation", length = 20)
    private Relation phoneOwnerRelation;

    @Column(name = "email_address", length = 254)
    private String emailAddress;

    @Column(name = "email_owner_name", length = 150)
    private String emailOwnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_owner_relation", length = 20)
    private Relation emailOwnerRelation;

    /** Who agreed to being reminded on the phone; null for nobody (D-77). */
    @Enumerated(EnumType.STRING)
    @Column(name = "phone_consent", length = 10)
    private Consent phoneConsent;

    /** Who agreed to being reminded at the email; null for nobody (D-77). */
    @Enumerated(EnumType.STRING)
    @Column(name = "email_consent", length = 10)
    private Consent emailConsent;

    @Column(name = "insurance_insurer", length = 100)
    private String insuranceInsurer;

    @Column(name = "insurance_member_number", length = 30)
    private String insuranceMemberNumber;

    @Column(name = "insurance_plan", length = 50)
    private String insurancePlan;

    /** At reception or on the web, from their registration on (D-91). */
    @Enumerated(EnumType.STRING)
    @Column(name = "registered_via", nullable = false, length = 10)
    private RegistrationChannel registeredVia;

    protected Patient() {
        // required by JPA
    }

    private Patient(UUID id, String fullName, IdentityDocument document, IdentityStatus identityStatus,
            String alias, RegistrationChannel registeredVia) {
        this.id = id;
        this.fullName = requireName(fullName);
        this.document = document;
        this.identityStatus = identityStatus;
        this.alias = alias;
        this.registeredVia = registeredVia;
    }

    /**
     * A new patient who gave a name: verified when reception saw their document as it registered
     * them, declared otherwise. The id is assigned here and not by the database (D-19).
     *
     * @throws UnverifiableIdentityException when the document is said to be seen and there is none
     */
    public static Patient register(String fullName, IdentityDocument document, boolean documentSeen) {
        return register(fullName, document, documentSeen, PatientDetails.NONE, null);
    }

    /**
     * A new patient who gave a name, with what else is known of them already. Registering is not a
     * change: nothing goes to the history of their details.
     *
     * @param today the practice's date, which a date of birth may not be after; unused without one
     * @throws InvalidPatientDetailsException when the date of birth cannot be right
     */
    public static Patient register(String fullName, IdentityDocument document, boolean documentSeen,
            PatientDetails details, LocalDate today) {
        if (documentSeen && document == null) {
            throw new UnverifiableIdentityException("An identity is verified on a document, and there is none");
        }
        Patient patient = new Patient(UUID.randomUUID(), fullName, document,
                documentSeen ? IdentityStatus.VERIFIED : IdentityStatus.DECLARED, null, RegistrationChannel.RECEPTION);
        patient.setDetails(requireBirthDate(details, today));
        return patient;
    }

    /**
     * A new patient who registered themselves on the web (D-91): with a document, which their account logs in
     * with, that nobody has seen yet, so their identity is declared until reception verifies it.
     *
     * @param today the practice's date, which a date of birth may not be after
     * @throws IllegalArgumentException when there is no document
     * @throws InvalidPatientDetailsException when the date of birth cannot be right
     */
    public static Patient registerFromWeb(String fullName, IdentityDocument document, PatientDetails details,
            LocalDate today) {
        if (document == null) {
            throw new IllegalArgumentException("A patient registers on the web with their document");
        }
        Patient patient = new Patient(UUID.randomUUID(), fullName, document, IdentityStatus.DECLARED, null,
                RegistrationChannel.WEB);
        patient.setDetails(requireBirthDate(details, today));
        return patient;
    }

    /** A new patient who gave no name, under an alias such as NN-4821, which stands as their name. */
    public static Patient registerUnidentified(String alias) {
        if (alias == null || !ALIAS_FORMAT.matcher(alias).matches()) {
            throw new IllegalArgumentException("An alias is NN- and four digits, such as NN-4821");
        }
        return new Patient(UUID.randomUUID(), alias, null, IdentityStatus.ALIAS, alias, RegistrationChannel.RECEPTION);
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
     * <p>A patient with a web account logs in with their document, so a new name or document goes in only with
     * the document seen (D-79): a verified one is never left declared. One who registered on the web is declared
     * until reception sees it (D-91), and saving them unchanged leaves them so.
     *
     * @param document null clears it, which is how a wrongly entered one is taken back out
     * @param documentSeen whether reception saw the document now
     * @param holdsAccount whether the patient has a web account, in any state
     * @param changedBy the username of whoever changes it
     * @throws UnverifiableIdentityException when it is said to be seen and cannot be
     * @throws VerificationRequiredException when it would change the name or document of a patient with a web
     *         account without the document seen
     */
    public Optional<PatientIdentityChange> changeIdentity(String fullName, IdentityDocument document,
            boolean documentSeen, boolean holdsAccount, LocalDateTime changedAt, String changedBy) {
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
        if (holdsAccount && changed && !documentSeen) {
            throw new VerificationRequiredException(
                    "A patient with a web account changes their name or document only with the document seen");
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
     * Changes what is known of the patient besides who they are, and says what changed: one entry
     * for each detail that did, none when nothing did (D-73). A null detail clears it.
     *
     * <p>A phone or an email whose number, address or owner changes loses its consent, which was given
     * for that one and that person, and the history says so too (D-77).
     *
     * @param today the practice's date, which a date of birth may not be after
     * @param changedBy the username of whoever changes it
     * @throws InvalidPatientDetailsException when the date of birth cannot be right
     */
    public List<PatientDetailsChange> changeDetails(PatientDetails details, LocalDate today,
            LocalDateTime changedAt, String changedBy) {
        PatientDetails before = getDetails();
        PatientDetails after = requireBirthDate(details, today);
        List<PatientDetailsChange> changes = new ArrayList<>();
        if (!Objects.equals(before.birthDate(), after.birthDate())) {
            changes.add(PatientDetailsChange.of(id, changedAt, changedBy, PatientDetailsChange.Field.BIRTH_DATE,
                    Objects.toString(before.birthDate(), null), null,
                    Objects.toString(after.birthDate(), null), null));
        }
        if (!Objects.equals(before.phone(), after.phone())) {
            changes.add(PatientDetailsChange.of(id, changedAt, changedBy, PatientDetailsChange.Field.PHONE,
                    before.phone() == null ? null : before.phone().number(),
                    before.phone() == null ? null : before.phone().owner(),
                    after.phone() == null ? null : after.phone().number(),
                    after.phone() == null ? null : after.phone().owner()));
        }
        if (!Objects.equals(before.email(), after.email())) {
            changes.add(PatientDetailsChange.of(id, changedAt, changedBy, PatientDetailsChange.Field.EMAIL,
                    before.email() == null ? null : before.email().address(),
                    before.email() == null ? null : before.email().owner(),
                    after.email() == null ? null : after.email().address(),
                    after.email() == null ? null : after.email().owner()));
        }
        if (!Objects.equals(before.insurance(), after.insurance())) {
            changes.add(PatientDetailsChange.ofInsurance(id, changedAt, changedBy, before.insurance(),
                    after.insurance()));
        }
        setDetails(after);
        changes.addAll(changeConsent(Objects.equals(before.phone(), after.phone()) ? phoneConsent : null,
                Objects.equals(before.email(), after.email()) ? emailConsent : null, changedAt, changedBy));
        return changes;
    }

    /**
     * The patient changes their own contact and health insurance from their web account (D-81): what
     * reception changes, with the same rules — a new number, address or owner loses its consent —, and their
     * date of birth as it was, since it is reception's to change.
     *
     * @param changedBy the username of whoever changes it, the patient's web account here
     * @throws InvalidPatientDetailsException when a value cannot be right
     */
    public List<PatientDetailsChange> changeContact(Phone phone, Email email, HealthInsurance insurance,
            LocalDate today, LocalDateTime changedAt, String changedBy) {
        return changeDetails(new PatientDetails(birthDate, phone, email, insurance), today, changedAt, changedBy);
    }

    /**
     * The patient says, on each of their channels, whether they agree to be reminded there (D-81): yes is
     * their own consent, no withdraws any — a responsible person's too, since the reminders are about the
     * patient's appointments —, and null leaves the channel as it is.
     *
     * @throws InvalidPatientDetailsException when they agree on a channel that is not there
     */
    public List<PatientDetailsChange> changeOwnConsent(Boolean phone, Boolean email, LocalDateTime changedAt,
            String changedBy) {
        return changeConsent(own(phone, phoneConsent), own(email, emailConsent), changedAt, changedBy);
    }

    private static Consent own(Boolean agrees, Consent current) {
        if (agrees == null) {
            return current;
        }
        return agrees ? Consent.PATIENT : null;
    }

    /**
     * Sets who agreed to the patient being reminded on each channel, and says what changed: one entry for
     * each consent that did (D-77). A null consent is none, which withdraws it.
     *
     * <p>A channel the patient has no number or address for takes no consent, and only a responsible
     * person's channel takes its owner's: on the patient's own, the patient is the owner.
     *
     * @param changedBy the username of whoever changes it
     * @throws InvalidPatientDetailsException when a consent is for a channel that is not there, or is its
     *         owner's on the patient's own channel
     */
    public List<PatientDetailsChange> changeConsent(Consent phone, Consent email, LocalDateTime changedAt,
            String changedBy) {
        Phone currentPhone = getPhone();
        Email currentEmail = getEmail();
        requireConsentFits(phone, currentPhone == null ? null : currentPhone.owner(), currentPhone != null, "phone");
        requireConsentFits(email, currentEmail == null ? null : currentEmail.owner(), currentEmail != null, "email");
        List<PatientDetailsChange> changes = new ArrayList<>();
        if (phone != phoneConsent) {
            changes.add(PatientDetailsChange.ofConsent(id, changedAt, changedBy,
                    PatientDetailsChange.Field.PHONE_CONSENT, phoneConsent, phone));
        }
        if (email != emailConsent) {
            changes.add(PatientDetailsChange.ofConsent(id, changedAt, changedBy,
                    PatientDetailsChange.Field.EMAIL_CONSENT, emailConsent, email));
        }
        this.phoneConsent = phone;
        this.emailConsent = email;
        return changes;
    }

    private static void requireConsentFits(Consent consent, Responsible owner, boolean channelThere, String channel) {
        if (consent == null) {
            return;
        }
        if (!channelThere) {
            throw new InvalidPatientDetailsException("A consent needs the " + channel + " it is for");
        }
        if (consent == Consent.OWNER && owner == null) {
            throw new InvalidPatientDetailsException(
                    "The patient's own " + channel + " takes the patient's consent, not an owner's");
        }
    }

    /**
     * A date of birth is not in the future, and not more than {@value #MAX_AGE_YEARS} years ago:
     * either is a slip of the keyboard, such as a mistyped year (D-38).
     */
    private static PatientDetails requireBirthDate(PatientDetails details, LocalDate today) {
        if (details == null) {
            return PatientDetails.NONE;
        }
        LocalDate birthDate = details.birthDate();
        if (birthDate != null && birthDate.isAfter(today)) {
            throw new InvalidPatientDetailsException("A date of birth is not in the future");
        }
        if (birthDate != null && birthDate.isBefore(today.minusYears(MAX_AGE_YEARS))) {
            throw new InvalidPatientDetailsException("A date of birth is at most " + MAX_AGE_YEARS + " years ago");
        }
        return details;
    }

    static final int MAX_AGE_YEARS = 130;

    private void setDetails(PatientDetails details) {
        this.birthDate = details.birthDate();
        Phone phone = details.phone();
        Responsible phoneOwner = phone == null ? null : phone.owner();
        this.phoneNumber = phone == null ? null : phone.number();
        this.phoneOwnerName = phoneOwner == null ? null : phoneOwner.name();
        this.phoneOwnerRelation = phoneOwner == null ? null : phoneOwner.relation();
        Email email = details.email();
        Responsible emailOwner = email == null ? null : email.owner();
        this.emailAddress = email == null ? null : email.address();
        this.emailOwnerName = emailOwner == null ? null : emailOwner.name();
        this.emailOwnerRelation = emailOwner == null ? null : emailOwner.relation();
        HealthInsurance insurance = details.insurance();
        this.insuranceInsurer = insurance == null ? null : insurance.insurer();
        this.insuranceMemberNumber = insurance == null ? null : insurance.memberNumber();
        this.insurancePlan = insurance == null ? null : insurance.plan();
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

    public RegistrationChannel getRegisteredVia() {
        return registeredVia;
    }

    public PatientDetails getDetails() {
        return new PatientDetails(birthDate, getPhone(), getEmail(), getInsurance());
    }

    /** Who agreed to being reminded on the phone; null for nobody (D-77). */
    public Consent getPhoneConsent() {
        return phoneConsent;
    }

    /** Who agreed to being reminded at the email; null for nobody (D-77). */
    public Consent getEmailConsent() {
        return emailConsent;
    }

    /** Null when not given. */
    public HealthInsurance getInsurance() {
        return insuranceInsurer == null ? null
                : new HealthInsurance(insuranceInsurer, insuranceMemberNumber, insurancePlan);
    }

    /** Null when not given. */
    public LocalDate getBirthDate() {
        return birthDate;
    }

    /** Null when not given. */
    public Phone getPhone() {
        return phoneNumber == null ? null : new Phone(phoneNumber, owner(phoneOwnerName, phoneOwnerRelation));
    }

    /** Null when not given. */
    public Email getEmail() {
        return emailAddress == null ? null : new Email(emailAddress, owner(emailOwnerName, emailOwnerRelation));
    }

    private static Responsible owner(String name, Relation relation) {
        return relation == null ? null : new Responsible(name, relation);
    }
}
