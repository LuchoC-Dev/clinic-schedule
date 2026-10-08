package com.clinic.schedule.portal.adapter.in.http;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.portal.application.usecase.ActivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.BookOnWebUseCase;
import com.clinic.schedule.portal.application.usecase.CancelLinkUseCase;
import com.clinic.schedule.portal.application.usecase.CancelOwnAppointmentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnPasswordUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnConsentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnContactUseCase;
import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.application.usecase.ManageOwnFamilyUseCase;
import com.clinic.schedule.portal.application.usecase.RecoverPasswordUseCase;
import com.clinic.schedule.portal.application.usecase.RegisterOnWebUseCase;
import com.clinic.schedule.portal.application.usecase.ViewFamilyUseCase;
import com.clinic.schedule.portal.application.usecase.ViewOwnDataUseCase;
import com.clinic.schedule.portal.application.usecase.ViewWebBookingUseCase;
import com.clinic.schedule.portal.domain.BookedAppointment;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.shared.http.ErrorResponse;

import jakarta.validation.Valid;

/**
 * The patients' side of the API (D-80), the contract of their single-page application to come: registering on
 * the web (D-91) and activating an account, before there is a login, and what a patient logged in reads and
 * changes of their own (D-81).
 * Whose it is comes from the login, never from the request.
 */
@RestController
class PortalController {

    private final ActivateAccountUseCase activate;
    private final ListOwnAppointmentsUseCase appointments;
    private final ViewOwnDataUseCase data;
    private final ChangeOwnContactUseCase contact;
    private final ChangeOwnConsentUseCase consent;
    private final ViewWebBookingUseCase booking;
    private final BookOnWebUseCase book;
    private final CancelOwnAppointmentUseCase cancel;
    private final CancelLinkUseCase cancelLinks;
    private final ViewFamilyUseCase family;
    private final ManageOwnFamilyUseCase ownFamily;
    private final RecoverPasswordUseCase recovery;
    private final ChangeOwnPasswordUseCase ownPassword;
    private final RegisterOnWebUseCase registration;
    private final PractitionerReader practitioners;

    PortalController(ActivateAccountUseCase activate, ListOwnAppointmentsUseCase appointments, ViewOwnDataUseCase data,
            ChangeOwnContactUseCase contact, ChangeOwnConsentUseCase consent, ViewWebBookingUseCase booking,
            BookOnWebUseCase book, CancelOwnAppointmentUseCase cancel, CancelLinkUseCase cancelLinks,
            ViewFamilyUseCase family, ManageOwnFamilyUseCase ownFamily, RecoverPasswordUseCase recovery,
            ChangeOwnPasswordUseCase ownPassword, RegisterOnWebUseCase registration, PractitionerReader practitioners) {
        this.activate = activate;
        this.appointments = appointments;
        this.data = data;
        this.contact = contact;
        this.consent = consent;
        this.booking = booking;
        this.book = book;
        this.cancel = cancel;
        this.cancelLinks = cancelLinks;
        this.family = family;
        this.ownFamily = ownFamily;
        this.recovery = recovery;
        this.ownPassword = ownPassword;
        this.registration = registration;
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
        return OwnAppointmentsResponse.of(appointments.handle(), appointments.family(), names);
    }

    /** The reasons booked on the web, by practitioner, the days the web books (D-82), and for whom (D-85). */
    @GetMapping("/api/portal/booking/options")
    BookingOptionsResponse bookingOptions() {
        return BookingOptionsResponse.of(booking.options(), family.bookableFor());
    }

    /** The times offered for a reason booked on the web, on a day the web books. */
    @GetMapping("/api/portal/booking/times")
    BookingTimesResponse bookingTimes(@RequestParam UUID practitionerId, @RequestParam UUID reasonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new BookingTimesResponse(date, booking.times(practitionerId, reasonId, date));
    }

    /** The patient books an appointment for themselves, or for a member of their family they act for. */
    @PostMapping("/api/portal/appointments")
    ResponseEntity<WebBookingResponse> book(@Valid @RequestBody WebBookingRequest request) {
        BookOnWebUseCase.Result booked = book.handle(request.patientId(), request.practitionerId(),
                request.reasonId(), request.startsAt());
        return ResponseEntity.created(URI.create("/api/portal/appointments/" + booked.appointmentId()))
                .body(WebBookingResponse.of(booked));
    }

    /** The patient cancels one of their appointments, or a member's, up to a day before it (D-83, D-85). */
    @PostMapping("/api/portal/appointments/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id) {
        cancel.handle(id);
        return ResponseEntity.noContent().build();
    }

    /** What an email's link would cancel, with nobody logged in: when, and with whom — nothing else (D-83). */
    @GetMapping("/api/portal/cancel-links/{token}")
    LinkedAppointmentResponse cancelLink(@PathVariable String token) {
        return linked(cancelLinks.read(token));
    }

    /** Cancels the appointment of an email's link, with nobody logged in. */
    @PostMapping("/api/portal/cancel-links/{token}/cancel")
    ResponseEntity<Void> cancelByLink(@PathVariable String token) {
        cancelLinks.cancel(token);
        return ResponseEntity.noContent().build();
    }

    private LinkedAppointmentResponse linked(BookedAppointment appointment) {
        return new LinkedAppointmentResponse(appointment.startsAt(), practitioners.findAll().stream()
                .filter(practitioner -> practitioner.id().equals(appointment.practitionerId()))
                .map(PractitionerSummary::fullName)
                .findFirst()
                .orElse(null));
    }

    /**
     * Sends the link to choose a new password to the account's email (D-86). Always the same answer, so it tells
     * nobody whether the document has an account.
     */
    @PostMapping("/api/portal/password-recovery")
    ResponseEntity<Void> recoverPassword(@RequestBody PasswordRequests.Recovery request) {
        recovery.ask(request.documentType(), request.documentNumber());
        return ResponseEntity.noContent().build();
    }

    /** With the token of the email's link, and the new password. */
    @PostMapping("/api/portal/password-reset")
    ResponseEntity<Void> resetPassword(@RequestBody PasswordRequests.Reset request) {
        recovery.reset(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * An adult registers themselves (D-91): 202 once the email went out, whichever it was — the link to confirm
     * it, or, for a document the practice knows already, the request to come to reception —, so it tells nobody
     * whether a document is a patient's; 503 when it did not go out, and nothing was kept.
     */
    @PostMapping("/api/portal/registrations")
    ResponseEntity<Object> register(@RequestBody RegistrationRequests.Registration request) {
        boolean sent = registration.request(new RegisterOnWebUseCase.Command(request.fullName(),
                request.documentType(), request.documentNumber(), request.birthDate(), request.phone(),
                request.email(), request.password()));
        if (!sent) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(503,
                    "Service unavailable", "The email could not go out; nothing was kept"));
        }
        return ResponseEntity.accepted().build();
    }

    /**
     * With the token of the email's link: 204 once the patient is registered with their account in use; 409 when
     * the document was someone's by then, and nothing was registered.
     */
    @PostMapping("/api/portal/registrations/confirmation")
    ResponseEntity<Object> confirmRegistration(@RequestBody RegistrationRequests.Confirmation request) {
        if (registration.confirm(request.token()) == RegisterOnWebUseCase.Confirmation.DOCUMENT_KNOWN) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict",
                    "The document is already the practice's: the registration is done at reception"));
        }
        return ResponseEntity.noContent().build();
    }

    /** The patient logged in changes their password, giving the current one. */
    @PostMapping("/api/portal/password")
    ResponseEntity<Void> changePassword(@RequestBody PasswordRequests.Change request) {
        ownPassword.handle(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** The patient's family groups, by name (D-85). */
    @GetMapping("/api/portal/family")
    List<FamilyResponse> family() {
        return family.groups().stream().map(FamilyResponse::of).toList();
    }

    /** Whether the group sees the patient's appointments: theirs to choose, from 13 on. */
    @PostMapping("/api/portal/family/{groupId}/sharing")
    List<FamilyResponse> share(@PathVariable UUID groupId, @RequestBody FamilyGroupRequests.Share request) {
        ownFamily.share(groupId, request.shares());
        return family();
    }

    /** The patient leaves the group; whoever else is in it stays. */
    @PostMapping("/api/portal/family/{groupId}/leave")
    List<FamilyResponse> leave(@PathVariable UUID groupId) {
        ownFamily.leave(groupId);
        return family();
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
