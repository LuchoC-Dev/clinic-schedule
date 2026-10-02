package com.clinic.schedule.portal.adapter.in.http;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.portal.application.usecase.ActivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnConsentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnContactUseCase;
import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.application.usecase.ViewOwnDataUseCase;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

import jakarta.validation.Valid;

/**
 * The patients' side of the API (D-80), the contract of their single-page application to come: activating
 * an account, before there is a login, and what a patient logged in reads and changes of their own (D-81).
 * Whose it is comes from the login, never from the request.
 */
@RestController
class PortalController {

    private final ActivateAccountUseCase activate;
    private final ListOwnAppointmentsUseCase appointments;
    private final ViewOwnDataUseCase data;
    private final ChangeOwnContactUseCase contact;
    private final ChangeOwnConsentUseCase consent;
    private final PractitionerReader practitioners;

    PortalController(ActivateAccountUseCase activate, ListOwnAppointmentsUseCase appointments, ViewOwnDataUseCase data,
            ChangeOwnContactUseCase contact, ChangeOwnConsentUseCase consent, PractitionerReader practitioners) {
        this.activate = activate;
        this.appointments = appointments;
        this.data = data;
        this.contact = contact;
        this.consent = consent;
        this.practitioners = practitioners;
    }

    /** With the token of the email's link, and the password the patient chooses. */
    @PostMapping("/api/portal/activation")
    ResponseEntity<Void> activate(@Valid @RequestBody ActivationRequest request) {
        activate.handle(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/portal/appointments")
    OwnAppointmentsResponse appointments() {
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        return OwnAppointmentsResponse.of(appointments.handle(), names);
    }

    /** The patient's own data (D-81). */
    @GetMapping("/api/portal/profile")
    ProfileResponse profile() {
        return ProfileResponse.of(data.handle());
    }

    /** Their contact and health insurance as they are to be: one left out is cleared. */
    @PutMapping("/api/portal/profile/details")
    ProfileResponse changeDetails(@RequestBody ProfileRequest request) {
        contact.handle(request.toContact());
        return ProfileResponse.of(data.handle());
    }

    /** Whether they agree to be reminded on each channel; one left out is left as it is. */
    @PutMapping("/api/portal/profile/contact-consent")
    ProfileResponse changeConsent(@RequestBody ConsentRequest request) {
        consent.handle(request.phone(), request.email());
        return ProfileResponse.of(data.handle());
    }
}
