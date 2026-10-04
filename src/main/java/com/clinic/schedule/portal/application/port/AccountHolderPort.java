package com.clinic.schedule.portal.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.AccountHolder;

/** What the patient module says of a patient a web account is, or would be, theirs. */
public interface AccountHolderPort {

    Optional<AccountHolder> find(UUID patientId);

    /** The patient of that document, as the login finds them (D-80). */
    Optional<UUID> findByDocument(String documentType, String documentNumber);

    /** Those of them that are there, in no order. */
    List<AccountHolder> findAll(Collection<UUID> patientIds);
}
