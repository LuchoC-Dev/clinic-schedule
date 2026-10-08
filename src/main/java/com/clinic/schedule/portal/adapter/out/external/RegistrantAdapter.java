package com.clinic.schedule.portal.adapter.out.external;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException;
import com.clinic.schedule.patient.api.PatientWriter;
import com.clinic.schedule.patient.api.WebPatient;
import com.clinic.schedule.portal.application.exception.InvalidRegistrationException;
import com.clinic.schedule.portal.application.port.RegistrantPort;
import com.clinic.schedule.portal.domain.Registrant;

/** Answers {@link RegistrantPort} through the patient module's api, translating its refusals (D-05, D-24). */
@Component
class RegistrantAdapter implements RegistrantPort {

    private final PatientReader reader;
    private final PatientWriter writer;

    RegistrantAdapter(PatientReader reader, PatientWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public void check(Registrant registrant) {
        try {
            writer.checkFromWeb(patient(registrant));
        } catch (PatientRegistrationRefusedException e) {
            throw new InvalidRegistrationException(e.getMessage());
        }
    }

    @Override
    public boolean isKnown(Registrant registrant) {
        return reader.findIdByDocument(registrant.documentType(), registrant.documentNumber()).isPresent();
    }

    /**
     * The document is looked up first rather than refused by the patient module: a refusal thrown from a
     * transactional method marks the caller's transaction for rollback, and the caller still forgets the
     * registration. Two confirmations of one document at the very same moment are refused by its unique constraint.
     */
    @Override
    public Optional<UUID> register(Registrant registrant) {
        if (isKnown(registrant)) {
            return Optional.empty();
        }
        try {
            return Optional.of(writer.registerFromWeb(patient(registrant)));
        } catch (PatientRegistrationRefusedException e) {
            throw new InvalidRegistrationException(e.getMessage());
        }
    }

    private static WebPatient patient(Registrant registrant) {
        return new WebPatient(registrant.fullName(), registrant.documentType(), registrant.documentNumber(),
                registrant.birthDate(), registrant.phoneNumber());
    }
}
