package com.clinic.schedule.patient.presentation.http;

import java.time.LocalDate;
import java.util.UUID;

import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.Email;
import com.clinic.schedule.patient.entity.HealthInsurance;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IdentityStatus;
import com.clinic.schedule.patient.entity.Patient;
import com.clinic.schedule.patient.entity.Phone;
import com.clinic.schedule.patient.entity.Relation;
import com.clinic.schedule.patient.entity.Responsible;

/**
 * A patient as the API returns it. The document comes back as a nested object, mirroring
 * that it is one value with two parts, and as null for a patient without one. {@code identity}
 * says how far the practice knows who they are, and {@code alias} is the one they went by when
 * they gave no name, null for everyone else (D-60).
 *
 * <p>The date of birth, the phone and the email are null when not given; the phone's and the email's
 * {@code owner} is null when they are the patient's own (D-73). So is the health insurance, whose
 * member number and plan are null when not given (D-74).
 */
record PatientResponse(UUID id, String fullName, Document document, IdentityStatus identity, String alias,
        LocalDate birthDate, PhoneResponse phone, EmailResponse email, InsuranceResponse insurance) {

    record InsuranceResponse(String insurer, String memberNumber, String plan) {

        static InsuranceResponse of(HealthInsurance insurance) {
            return insurance == null ? null
                    : new InsuranceResponse(insurance.insurer(), insurance.memberNumber(), insurance.plan());
        }
    }

    record Document(DocumentType type, String number) {
    }

    record Owner(String name, Relation relation) {

        static Owner of(Responsible owner) {
            return owner == null ? null : new Owner(owner.name(), owner.relation());
        }
    }

    record PhoneResponse(String number, Owner owner) {

        static PhoneResponse of(Phone phone) {
            return phone == null ? null : new PhoneResponse(phone.number(), Owner.of(phone.owner()));
        }
    }

    record EmailResponse(String address, Owner owner) {

        static EmailResponse of(Email email) {
            return email == null ? null : new EmailResponse(email.address(), Owner.of(email.owner()));
        }
    }

    static PatientResponse of(Patient patient) {
        IdentityDocument document = patient.getDocument();
        return new PatientResponse(
                patient.getId(),
                patient.getFullName(),
                document == null ? null : new Document(document.type(), document.number()),
                patient.getIdentityStatus(),
                patient.getAlias(),
                patient.getBirthDate(),
                PhoneResponse.of(patient.getPhone()),
                EmailResponse.of(patient.getEmail()),
                InsuranceResponse.of(patient.getInsurance()));
    }
}
