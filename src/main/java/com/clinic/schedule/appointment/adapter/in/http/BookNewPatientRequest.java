package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.appointment.application.port.PatientRegistrationPort.NewPatient;
import com.clinic.schedule.appointment.application.usecase.BookNewPatientUseCase;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PractitionerId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A booking for a patient registered in the same step (D-75, D-100): the booking of {@link BookAppointmentRequest},
 * with the patient to register in place of their id. The patient is the patient module's to check: a refusal there
 * books nothing, and a refused booking registers nobody.
 *
 * <p>The phone and the email each say whose they are, as when registering a patient (D-73): with no owner, the
 * patient's own; otherwise a name and a relation, such as {@code MOTHER}.
 */
record BookNewPatientRequest(
        UUID id,

        @NotNull(message = "the patient is required")
        @Valid
        Patient patient,

        @NotNull(message = "the practitioner is required")
        UUID practitionerId,

        @NotNull(message = "the reason is required")
        UUID reasonId,

        @NotNull(message = "the start time is required")
        LocalDateTime startsAt,

        @Size(max = 500, message = "the note is at most 500 characters")
        String note) {

    record Patient(
            @Size(max = 150, message = "the full name is at most 150 characters")
            String fullName,
            String documentType,
            @Size(max = 20, message = "the document number is at most 20 characters")
            String documentNumber,
            Contact phone,
            Contact email) {
    }

    /** {@code owner} null when it is the patient's own. */
    record Contact(String value, Owner owner) {
    }

    record Owner(String name, String relation) {
    }

    BookNewPatientUseCase.Command toCommand() {
        return new BookNewPatientUseCase.Command(
                id == null ? AppointmentId.newId() : new AppointmentId(id),
                new NewPatient(patient.fullName(), patient.documentType(), patient.documentNumber(),
                        value(patient.phone()), relation(patient.phone()), ownerName(patient.phone()),
                        value(patient.email()), relation(patient.email()), ownerName(patient.email())),
                new PractitionerId(practitionerId),
                reasonId,
                startsAt,
                note);
    }

    private static String value(Contact contact) {
        return contact == null ? null : contact.value();
    }

    private static String relation(Contact contact) {
        return contact == null || contact.owner() == null ? null : contact.owner().relation();
    }

    private static String ownerName(Contact contact) {
        return contact == null || contact.owner() == null ? null : contact.owner().name();
    }
}
