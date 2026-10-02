package com.clinic.schedule.patient.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.patient.api.NewPatient;
import com.clinic.schedule.patient.api.PatientContact;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException.Reason;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.patient.api.PatientWriter;
import com.clinic.schedule.patient.entity.Consent;
import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.Email;
import com.clinic.schedule.patient.entity.HealthInsurance;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IdentityStatus;
import com.clinic.schedule.patient.entity.IncompleteDocumentException;
import com.clinic.schedule.patient.entity.InvalidPatientDetailsException;
import com.clinic.schedule.patient.entity.Patient;
import com.clinic.schedule.patient.entity.PatientDetails;
import com.clinic.schedule.patient.entity.PatientDetailsChange;
import com.clinic.schedule.patient.entity.PatientIdentityChange;
import com.clinic.schedule.patient.entity.Phone;
import com.clinic.schedule.patient.entity.Relation;
import com.clinic.schedule.patient.entity.Responsible;
import com.clinic.schedule.patient.persistence.PatientDetailsChangeRepository;
import com.clinic.schedule.patient.persistence.PatientIdentityChangeRepository;
import com.clinic.schedule.patient.persistence.PatientRepository;
import com.clinic.schedule.security.api.CurrentUserReader;

/**
 * Registering and finding patients, and answering other modules through
 * {@link PatientReader} and {@link PatientWriter}. It is public because the presentation layer
 * lives in another package; other modules still reach it only as those interfaces, which
 * {@code ArchitectureTest} checks now that visibility no longer can (D-24, D-42).
 *
 * <p>The document is checked here before saving so the answer is a clear 409 instead of a
 * constraint violation, but the constraint in the database is the one that actually
 * guarantees it (D-09). Under a race the insert fails, which is the correct outcome.
 *
 * <p>Each method declares its own transaction and every write calls {@code save()}, even
 * where Hibernate would write the change by itself (D-27).
 *
 * <p>A change to a patient's identity is saved with its entry in the history, in the same
 * transaction, stamped with the practice's clock and the user who made it (D-60), and so is a change
 * to their details (D-73).
 */
@Service
public class PatientService implements PatientReader, PatientWriter {

    /** Tries before giving up on a free alias: with a few hundred patients, one is plenty. */
    private static final int ALIAS_ATTEMPTS = 20;

    private final PatientRepository patients;
    private final PatientIdentityChangeRepository identityChanges;
    private final PatientDetailsChangeRepository detailsChanges;
    private final CurrentUserReader currentUser;
    private final Clock clock;

    PatientService(PatientRepository patients, PatientIdentityChangeRepository identityChanges,
            PatientDetailsChangeRepository detailsChanges, CurrentUserReader currentUser, Clock clock) {
        this.patients = patients;
        this.identityChanges = identityChanges;
        this.detailsChanges = detailsChanges;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public Patient register(String fullName, IdentityDocument document, boolean documentSeen) {
        return register(fullName, document, documentSeen, PatientDetails.NONE);
    }

    /** A new patient with what else is known of them already (D-73). */
    @Transactional
    public Patient register(String fullName, IdentityDocument document, boolean documentSeen, PatientDetails details) {
        requireDocumentIsFree(document);
        return patients.save(Patient.register(fullName, document, documentSeen, details, LocalDate.now(clock)));
    }

    /**
     * Registration as other modules ask for it, in plain values, with the phone and the email (D-75).
     * This module's own refusals are translated into the one the api declares, so the caller need not
     * know them. It joins the caller's transaction, so a caller that fails afterwards takes the
     * patient back with it.
     */
    @Override
    @Transactional
    public UUID register(NewPatient patient) {
        try {
            DocumentType type = blankToNull(patient.documentType()) == null
                    ? null : DocumentType.valueOf(patient.documentType());
            PatientDetails details = new PatientDetails(null,
                    Phone.ofNullable(patient.phoneNumber(),
                            Responsible.ofNullable(patient.phoneOwnerName(), relation(patient.phoneOwnerRelation()))),
                    Email.ofNullable(patient.emailAddress(),
                            Responsible.ofNullable(patient.emailOwnerName(), relation(patient.emailOwnerRelation()))),
                    null);
            return register(patient.fullName(), IdentityDocument.ofNullable(type, patient.documentNumber()), false,
                    details).getId();
        } catch (DuplicateDocumentException e) {
            throw new PatientRegistrationRefusedException(Reason.DUPLICATE_DOCUMENT, e.getMessage());
        } catch (IncompleteDocumentException e) {
            throw new PatientRegistrationRefusedException(Reason.INCOMPLETE_DOCUMENT, e.getMessage());
        } catch (InvalidPatientDetailsException e) {
            throw new PatientRegistrationRefusedException(Reason.INVALID_DETAILS, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new PatientRegistrationRefusedException(Reason.INVALID_DATA, e.getMessage());
        }
    }

    private static Relation relation(String name) {
        return blankToNull(name) == null ? null : Relation.valueOf(name);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    /**
     * A patient who gives no name, under a random alias no other patient has, such as NN-4821.
     * The unique constraint on the alias is what guarantees it; checking first only keeps a
     * clash from being an error.
     */
    @Override
    @Transactional
    public UUID registerUnidentified() {
        return registerUnidentifiedPatient().getId();
    }

    @Transactional
    public Patient registerUnidentifiedPatient() {
        for (int attempt = 0; attempt < ALIAS_ATTEMPTS; attempt++) {
            String alias = "NN-%04d".formatted(ThreadLocalRandom.current().nextInt(10_000));
            if (!patients.existsByAlias(alias)) {
                return patients.save(Patient.registerUnidentified(alias));
            }
        }
        throw new IllegalStateException("No free alias was found for a patient without a name");
    }

    /**
     * Corrects who the patient is: their name, their document, and whether reception saw the
     * document now. What changed goes to the history; nothing is recorded when nothing changed.
     */
    @Transactional
    public Patient changeIdentity(UUID id, String fullName, IdentityDocument document, boolean documentSeen) {
        Patient patient = patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
        if (!Objects.equals(document, patient.getDocument())) {
            requireDocumentIsFree(document);
        }
        patient.changeIdentity(fullName, document, documentSeen, LocalDateTime.now(clock), currentUser.username())
                .ifPresent(identityChanges::save);
        return patients.save(patient);
    }

    /** Every change to the patient's identity, the most recent first. */
    @Transactional(readOnly = true)
    public List<PatientIdentityChange> identityHistory(UUID id) {
        if (!patients.existsById(id)) {
            throw new PatientNotFoundException(id);
        }
        return identityChanges.findByPatientIdOrderByChangedAtDesc(id);
    }

    /**
     * Changes what is known of the patient besides who they are. Each detail that changed goes to
     * the history; nothing is recorded when nothing changed (D-73).
     */
    @Transactional
    public Patient changeDetails(UUID id, PatientDetails details) {
        Patient patient = patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
        detailsChanges.saveAll(patient.changeDetails(details, LocalDate.now(clock), LocalDateTime.now(clock),
                currentUser.username()));
        return patients.save(patient);
    }

    /**
     * Sets who agreed to the patient being reminded on each channel; each consent that changed goes to
     * the history of the details (D-77).
     */
    @Transactional
    public Patient changeConsent(UUID id, Consent phone, Consent email) {
        Patient patient = patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
        detailsChanges.saveAll(patient.changeConsent(phone, email, LocalDateTime.now(clock), currentUser.username()));
        return patients.save(patient);
    }

    /** Every change to the patient's details, the most recent first. */
    @Transactional(readOnly = true)
    public List<PatientDetailsChange> detailsHistory(UUID id) {
        if (!patients.existsById(id)) {
            throw new PatientNotFoundException(id);
        }
        return detailsChanges.findByPatientIdOrderByChangedAtDescFieldAsc(id);
    }

    @Transactional(readOnly = true)
    public Patient byId(UUID id) {
        return patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
    }

    /**
     * Reception's search box: part of a name or the document number. An empty search lists
     * everyone, which is usable because a small practice has hundreds of patients, not
     * thousands.
     */
    @Transactional(readOnly = true)
    public List<Patient> searchPatients(String query) {
        if (query == null || query.isBlank()) {
            return patients.findAllByOrderByFullNameAsc();
        }
        String term = query.strip();
        return patients.findByFullNameContainingIgnoreCaseOrDocumentNumberContainingOrAliasContainingIgnoreCaseOrderByFullNameAsc(
                term, term, term);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID patientId) {
        return patients.existsById(patientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientSummary> search(String query) {
        return summaries(searchPatients(query));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientSummary> findAll(Collection<UUID> patientIds) {
        return summaries(patients.findAllById(patientIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientContact> contactsOf(Collection<UUID> patientIds) {
        return patients.findAllById(patientIds).stream()
                .map(PatientService::contact)
                .toList();
    }

    /** A patient under an alias is not named, and a channel's consent goes with it (D-77, D-78). */
    private static PatientContact contact(Patient patient) {
        Email email = patient.getEmail();
        return new PatientContact(patient.getId(),
                patient.getIdentityStatus() == IdentityStatus.ALIAS ? null : patient.getFullName(),
                patient.getBirthDate(),
                email == null ? null : new PatientContact.Channel(email.address(),
                        email.owner() == null ? null : email.owner().name(),
                        patient.getEmailConsent() == null ? null
                                : PatientContact.ConsentGiver.valueOf(patient.getEmailConsent().name())));
    }

    @Override
    public List<String> documentTypes() {
        return Arrays.stream(DocumentType.values()).map(DocumentType::name).toList();
    }

    @Override
    public List<String> relations() {
        return Arrays.stream(Relation.values()).map(Relation::name).toList();
    }

    private static List<PatientSummary> summaries(List<Patient> found) {
        return found.stream()
                .map(patient -> new PatientSummary(patient.getId(), patient.getFullName(), label(patient.getDocument()),
                        patient.getIdentityStatus() == IdentityStatus.VERIFIED, patient.getBirthDate(),
                        insurance(patient.getInsurance())))
                .toList();
    }

    private static PatientSummary.Insurance insurance(HealthInsurance insurance) {
        return insurance == null ? null
                : new PatientSummary.Insurance(insurance.insurer(), insurance.memberNumber(), insurance.plan());
    }

    private static String label(IdentityDocument document) {
        return document == null ? null : document.type() + " " + document.number();
    }

    private void requireDocumentIsFree(IdentityDocument document) {
        if (document != null && patients.existsByDocument(document)) {
            throw new DuplicateDocumentException(document);
        }
    }
}
