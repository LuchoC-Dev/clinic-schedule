package com.clinic.schedule.patient.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * What other modules may ask about patients. The only public way into this module besides
 * its HTTP endpoints and screens (D-24): everything else in the package is package-private.
 */
public interface PatientReader {

    boolean exists(UUID patientId);

    /** Reception's search box: part of a name or a document number. No query lists everyone. */
    List<PatientSummary> search(String query);

    /** The patients with those ids; unknown ids are left out. For showing names next to appointments. */
    List<PatientSummary> findAll(Collection<UUID> patientIds);
}
