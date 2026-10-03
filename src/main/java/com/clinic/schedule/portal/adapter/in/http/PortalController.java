package com.clinic.schedule.portal.adapter.in.http;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.clinic.schedule.portal.application.usecase.ChangeOwnConsentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnContactUseCase;
import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.application.usecase.ViewOwnDataUseCase;
import com.clinic.schedule.portal.application.usecase.ViewWebBookingUseCase;
import com.clinic.schedule.portal.domain.BookedAppointment;
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
    private final ViewWebBookingUseCase booking;
    private final BookOnWebUseCase book;
    private final CancelOwnAppointmentUseCase cancel;
    private final CancelLinkUseCase cancelLinks;
    private final PractitionerReader practitioners;

    PortalController(ActivateAccountUseCase activate, ListOwnAppointmentsUseCase appointments, ViewOwnDataUseCase data,
            ChangeOwnContactUseCase contact, ChangeOwnConsentUseCase consent, ViewWebBookingUseCase booking,
            BookOnWebUseCase book, CancelOwnAppointmentUseCase cancel, CancelLinkUseCase cancelLinks,
            PractitionerReader practitioners) {
        this.activate = activate;
        this.appointments = appointments;
        this.data = data;
        this.contact = contact;
        this.consent = consent;
        this.booking = booking;
        this.book = book;
        this.cancel = cancel;
        this.cancelLinks = cancelLinks;
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

    /** The reasons booked on the web, by practitioner, and the days the web books (D-82). */
    @GetMapping("/api/portal/booking/options")
    BookingOptionsResponse bookingOptions() {
        return BookingOptionsResponse.of(booking.options());
    }

    /** The times offered for a reason booked on the web, on a day the web books. */
    @GetMapping("/api/portal/booking/times")
    BookingTimesResponse bookingTimes(@RequestParam UUID practitionerId, @RequestParam UUID reasonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new BookingTimesResponse(date, booking.times(practitionerId, reasonId, date));
    }

    /** The patient books an appointment for themselves. */
    @PostMapping("/api/portal/appointments")
    ResponseEntity<WebBookingResponse> book(@Valid @RequestBody WebBookingRequest request) {
        BookOnWebUseCase.Result booked = book.handle(request.practitionerId(), request.reasonId(), request.startsAt());
        return ResponseEntity.created(URI.create("/api/portal/appointments/" + booked.appointmentId()))
                .body(WebBookingResponse.of(booked));
    }

    /** The patient cancels one of their appointments, up to a day before it (D-83). */
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
