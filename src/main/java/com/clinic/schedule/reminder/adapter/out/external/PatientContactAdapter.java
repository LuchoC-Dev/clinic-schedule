package com.clinic.schedule.reminder.adapter.out.external;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.clinic.schedule.patient.api.PatientContact;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.reminder.application.port.PatientContactPort;
import com.clinic.schedule.reminder.domain.Consent;
import com.clinic.schedule.reminder.domain.Contact;

/** Answers {@link PatientContactPort} by asking the patient module through its api (D-05). */
@Component
class PatientContactAdapter implements PatientContactPort {

    private final PatientReader patients;

    PatientContactAdapter(PatientReader patients) {
        this.patients = patients;
    }

    @Override
    public Map<UUID, Contact> contactsOf(Collection<UUID> patientIds) {
        return patients.contactsOf(patientIds).stream()
                .collect(Collectors.toMap(PatientContact::id, PatientContactAdapter::contact));
    }

    private static Contact contact(PatientContact patient) {
        PatientContact.Channel email = patient.email();
        return new Contact(patient.fullName(), patient.birthDate(),
                email == null ? null : email.address(),
                email == null ? null : email.ownerName(),
                email == null || email.consent() == null ? null : Consent.valueOf(email.consent().name()));
    }
}
