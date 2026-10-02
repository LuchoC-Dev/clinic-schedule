package com.clinic.schedule.patient.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about patients. The only public way into this module besides
 * its HTTP endpoints and screens (D-24): everything else in the package is package-private.
 */
public interface PatientReader {

    boolean exists(UUID patientId);

    /** Reception's search box: part of a name or a document number. No query lists everyone. */
    List<PatientSummary> search(String query);

    /**
     * The patient with that document — a type such as "DNI", and the number as typed, separators and all —,
     * for a patient logging in to their web account (D-80). Nobody when the document is not one.
     */
    Optional<UUID> findIdByDocument(String documentType, String documentNumber);

    /** The patient's own data, for the patient to read on their web account (D-81). */
    Optional<PatientProfile> profileOf(UUID patientId);

    /** The patients with those ids; unknown ids are left out. For showing names next to appointments. */
    List<PatientSummary> findAll(Collection<UUID> patientIds);

    /**
     * How to remind the patients with those ids, for the reminders and nothing else (D-78); unknown ids
     * are left out.
     */
    List<PatientContact> contactsOf(Collection<UUID> patientIds);

    /** The kinds of document a patient may identify with, such as "DNI", for a screen to offer. */
    List<String> documentTypes();

    /** Who the owner of a phone or an email may be to the patient, such as "MOTHER", for a screen to offer. */
    List<String> relations();
}
