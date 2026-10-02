package com.clinic.schedule.portal.adapter.out.external;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.patient.api.ContactChange;
import com.clinic.schedule.patient.api.PatientChangeRefusedException;
import com.clinic.schedule.patient.api.PatientProfile;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientWriter;
import com.clinic.schedule.portal.application.exception.OwnDataRefusedException;
import com.clinic.schedule.portal.application.port.PatientDataPort;
import com.clinic.schedule.portal.domain.OwnContact;
import com.clinic.schedule.portal.domain.OwnData;

/** Answers {@link PatientDataPort} through the patient module's api, translating its refusal (D-05, D-24). */
@Component
class PatientDataAdapter implements PatientDataPort {

    private final PatientReader reader;
    private final PatientWriter writer;

    PatientDataAdapter(PatientReader reader, PatientWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    @Override
    public Optional<OwnData> of(UUID patientId) {
        return reader.profileOf(patientId).map(PatientDataAdapter::data);
    }

    @Override
    public void changeContact(UUID patientId, OwnContact contact) {
        try {
            writer.changeContact(patientId, new ContactChange(contact.phoneNumber(), contact.phoneOwnerName(),
                    contact.phoneOwnerRelation(), contact.emailAddress(), contact.emailOwnerName(),
                    contact.emailOwnerRelation(), contact.insurer(), contact.memberNumber(), contact.plan()));
        } catch (PatientChangeRefusedException e) {
            throw new OwnDataRefusedException(e.getMessage());
        }
    }

    @Override
    public void changeConsent(UUID patientId, Boolean phone, Boolean email) {
        try {
            writer.changeOwnConsent(patientId, phone, email);
        } catch (PatientChangeRefusedException e) {
            throw new OwnDataRefusedException(e.getMessage());
        }
    }

    private static OwnData data(PatientProfile profile) {
        PatientProfile.Insurance insurance = profile.insurance();
        return new OwnData(profile.fullName(), profile.document(), profile.birthDate(), channel(profile.phone()),
                channel(profile.email()), insurance == null ? null
                        : new OwnData.Insurance(insurance.insurer(), insurance.memberNumber(), insurance.plan()));
    }

    private static OwnData.Channel channel(PatientProfile.Channel channel) {
        return channel == null ? null : new OwnData.Channel(channel.value(), channel.ownerName(),
                channel.ownerRelation(),
                channel.consent() == null ? null : OwnData.Consent.valueOf(channel.consent().name()));
    }
}
