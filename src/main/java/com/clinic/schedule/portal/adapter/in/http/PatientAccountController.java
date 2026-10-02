package com.clinic.schedule.portal.adapter.in.http;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.portal.application.exception.AccountNotFoundException;
import com.clinic.schedule.portal.application.usecase.ChangeAccountEmailUseCase;
import com.clinic.schedule.portal.application.usecase.DeactivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.FindAccountUseCase;
import com.clinic.schedule.portal.application.usecase.OpenAccountUseCase;
import com.clinic.schedule.portal.application.usecase.ReactivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.SendActivationUseCase;

import jakarta.validation.Valid;

/**
 * Reception's side of the patients' web accounts (D-79): opening one, sending its activation again, changing
 * its email, switching it off and on, and reading its history. Reception's alone, which the route decides
 * (D-34). Business operations are POSTs on a sub-path (D-23).
 */
@RestController
class PatientAccountController {

    private final OpenAccountUseCase open;
    private final SendActivationUseCase activation;
    private final ChangeAccountEmailUseCase emailChange;
    private final DeactivateAccountUseCase deactivate;
    private final ReactivateAccountUseCase reactivate;
    private final FindAccountUseCase accounts;

    PatientAccountController(OpenAccountUseCase open, SendActivationUseCase activation,
            ChangeAccountEmailUseCase emailChange, DeactivateAccountUseCase deactivate,
            ReactivateAccountUseCase reactivate, FindAccountUseCase accounts) {
        this.open = open;
        this.activation = activation;
        this.emailChange = emailChange;
        this.deactivate = deactivate;
        this.reactivate = reactivate;
        this.accounts = accounts;
    }

    @PostMapping("/api/patient-accounts")
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        OpenAccountUseCase.Result opened = open.handle(
                new OpenAccountUseCase.Command(request.patientId(), request.email()));
        return ResponseEntity.created(URI.create("/api/patient-accounts/" + opened.account().id()))
                .body(AccountResponse.of(opened.account(), opened.activationSent()));
    }

    /** The patient's account, in any state; 404 when they have none. */
    @GetMapping("/api/patient-accounts")
    AccountResponse ofPatient(@RequestParam UUID patientId) {
        return AccountResponse.of(accounts.byPatient(patientId)
                .orElseThrow(() -> new AccountNotFoundException(patientId)));
    }

    @GetMapping("/api/patient-accounts/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.of(accounts.byId(id));
    }

    @PostMapping("/api/patient-accounts/{id}/activation-resend")
    AccountResponse resendActivation(@PathVariable UUID id) {
        boolean sent = activation.handle(id);
        return AccountResponse.of(accounts.byId(id), sent);
    }

    @PostMapping("/api/patient-accounts/{id}/email-change")
    AccountResponse changeEmail(@PathVariable UUID id, @Valid @RequestBody EmailChangeRequest request) {
        ChangeAccountEmailUseCase.Result changed = emailChange.handle(id, request.email());
        return AccountResponse.of(changed.account(), changed.activationSent());
    }

    @PostMapping("/api/patient-accounts/{id}/deactivate")
    AccountResponse deactivate(@PathVariable UUID id) {
        return AccountResponse.of(deactivate.handle(id));
    }

    @PostMapping("/api/patient-accounts/{id}/reactivate")
    AccountResponse reactivate(@PathVariable UUID id) {
        return AccountResponse.of(reactivate.handle(id));
    }

    /** What was done to the account, the most recent first. */
    @GetMapping("/api/patient-accounts/{id}/changes")
    List<AccountChangeResponse> changes(@PathVariable UUID id) {
        return accounts.history(id).stream().map(AccountChangeResponse::of).toList();
    }
}
