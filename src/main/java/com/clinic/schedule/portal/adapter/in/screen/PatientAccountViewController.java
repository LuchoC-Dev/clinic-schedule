package com.clinic.schedule.portal.adapter.in.screen;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.portal.application.exception.AccountNotFoundException;
import com.clinic.schedule.portal.application.usecase.ChangeAccountEmailUseCase;
import com.clinic.schedule.portal.application.usecase.DeactivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.FindAccountUseCase;
import com.clinic.schedule.portal.application.usecase.OpenAccountUseCase;
import com.clinic.schedule.portal.application.usecase.ReactivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.SendActivationUseCase;
import com.clinic.schedule.portal.domain.AccountChange;
import com.clinic.schedule.portal.domain.AccountStatus;
import com.clinic.schedule.portal.domain.PatientAccount;
import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.AccountStateException;
import com.clinic.schedule.portal.domain.exception.InvalidAccountEmailException;

/**
 * Reception's screen of a patient's web account (D-79), reached from the patient's screen by a plain link:
 * the patient module never reads this one, which asks it whether a patient has an account. Every action
 * answers with a redirect back, saying what happened (D-33).
 */
@Controller
class PatientAccountViewController {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    private final FindAccountUseCase accounts;
    private final OpenAccountUseCase open;
    private final SendActivationUseCase activation;
    private final ChangeAccountEmailUseCase emailChange;
    private final DeactivateAccountUseCase deactivate;
    private final ReactivateAccountUseCase reactivate;
    private final PatientReader patients;

    PatientAccountViewController(FindAccountUseCase accounts, OpenAccountUseCase open,
            SendActivationUseCase activation, ChangeAccountEmailUseCase emailChange,
            DeactivateAccountUseCase deactivate, ReactivateAccountUseCase reactivate, PatientReader patients) {
        this.accounts = accounts;
        this.open = open;
        this.activation = activation;
        this.emailChange = emailChange;
        this.deactivate = deactivate;
        this.reactivate = reactivate;
        this.patients = patients;
    }

    @GetMapping("/patient-accounts")
    String account(@RequestParam UUID patient, Model model) {
        PatientSummary summary = patients.findAll(List.of(patient)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No patient " + patient));
        model.addAttribute("patient", summary);
        accounts.byPatient(patient).ifPresentOrElse(account -> {
            model.addAttribute("account", account);
            model.addAttribute("status", status(account.status()));
            model.addAttribute("history", accounts.history(account.id()).stream().map(HistoryRow::of).toList());
        }, () -> model.addAttribute("history", List.of()));
        return "patient-account";
    }

    @PostMapping("/patient-accounts")
    String open(@RequestParam UUID patientId, @RequestParam String email, RedirectAttributes redirect) {
        try {
            OpenAccountUseCase.Result opened = open.handle(new OpenAccountUseCase.Command(patientId, email));
            redirect.addFlashAttribute(opened.activationSent() ? "message" : "error", opened.activationSent()
                    ? "Cuenta abierta: le enviamos el link de activación a " + opened.account().email() + "."
                    : "Cuenta abierta, pero el email de activación no salió: reenvialo.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", refusal(e));
        }
        return back(patientId);
    }

    @PostMapping("/patient-accounts/{id}/activation-resend")
    String resendActivation(@PathVariable UUID id, RedirectAttributes redirect) {
        PatientAccount account = byId(id);
        try {
            boolean sent = activation.handle(id);
            redirect.addFlashAttribute(sent ? "message" : "error", sent
                    ? "Le reenviamos el link de activación a " + account.email() + ". El anterior ya no sirve."
                    : "El email de activación no salió: probá de nuevo más tarde.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", refusal(e));
        }
        return back(account.patientId());
    }

    @PostMapping("/patient-accounts/{id}/email-change")
    String changeEmail(@PathVariable UUID id, @RequestParam String email, RedirectAttributes redirect) {
        PatientAccount account = byId(id);
        try {
            ChangeAccountEmailUseCase.Result changed = emailChange.handle(id, email);
            if (changed.account().status() != AccountStatus.PENDING) {
                redirect.addFlashAttribute("message", "Email de la cuenta: " + changed.account().email() + ".");
            } else {
                redirect.addFlashAttribute(changed.activationSent() ? "message" : "error", changed.activationSent()
                        ? "Email cambiado: le enviamos el link de activación a " + changed.account().email() + "."
                        : "Email cambiado, pero el link de activación no salió: reenvialo.");
            }
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", refusal(e));
        }
        return back(account.patientId());
    }

    @PostMapping("/patient-accounts/{id}/deactivate")
    String deactivate(@PathVariable UUID id, RedirectAttributes redirect) {
        PatientAccount account = byId(id);
        try {
            deactivate.handle(id);
            redirect.addFlashAttribute("message", "Cuenta desactivada: ya no puede ingresar.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", refusal(e));
        }
        return back(account.patientId());
    }

    @PostMapping("/patient-accounts/{id}/reactivate")
    String reactivate(@PathVariable UUID id, RedirectAttributes redirect) {
        PatientAccount account = byId(id);
        try {
            PatientAccount reactivated = reactivate.handle(id);
            redirect.addFlashAttribute("message", reactivated.status() == AccountStatus.ACTIVE
                    ? "Cuenta reactivada: puede ingresar con su contraseña."
                    : "Cuenta reactivada: nunca se activó, así que reenviale el link de activación.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", refusal(e));
        }
        return back(account.patientId());
    }

    private PatientAccount byId(UUID id) {
        try {
            return accounts.byId(id);
        } catch (AccountNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private static String back(UUID patientId) {
        return "redirect:/patient-accounts?patient=" + patientId;
    }

    static String status(AccountStatus status) {
        return switch (status) {
            case PENDING -> "Pendiente de activación";
            case ACTIVE -> "Activa";
            case INACTIVE -> "Desactivada";
        };
    }

    /** What reception reads when it is refused; anything else is not a refusal and goes on. */
    private static String refusal(RuntimeException e) {
        if (e instanceof AccountRefusedException refused) {
            return switch (refused.reason()) {
                case ALREADY_OPENED -> "El paciente ya tiene cuenta web.";
                case IDENTITY_NOT_VERIFIED ->
                        "Para abrirle una cuenta web, primero verificá su identidad: mirá su documento y marcalo.";
                case NO_BIRTH_DATE -> "Para abrirle una cuenta web, cargá primero su fecha de nacimiento.";
                case UNDER_THIRTEEN -> "La cuenta web es para pacientes de 13 años o más.";
            };
        }
        if (e instanceof InvalidAccountEmailException) {
            return "El email tiene la forma nombre@dominio.com y hasta 254 caracteres.";
        }
        if (e instanceof AccountStateException) {
            return "La cuenta ya no está en ese estado: mirá cómo quedó.";
        }
        throw e;
    }

    /** An entry of the account's history, in Spanish (D-13). */
    record HistoryRow(String when, String action, String by) {

        static HistoryRow of(AccountChange change) {
            return new HistoryRow(WHEN.format(change.at()), switch (change.action()) {
                case OPENED -> "Abierta";
                case ACTIVATION_SENT -> "Link de activación enviado";
                case ACTIVATED -> "Activada";
                case EMAIL_CHANGED -> "Email cambiado";
                case DEACTIVATED -> "Desactivada";
                case REACTIVATED -> "Reactivada";
                case RECOVERY_SENT -> "Link para elegir contraseña enviado";
                case PASSWORD_RESET -> "Contraseña nueva desde el link";
                case PASSWORD_CHANGED -> "Contraseña cambiada";
                case LOCKED -> "Bloqueada por intentos fallidos";
            }, change.by() != null ? change.by() : switch (change.action()) {
                case RECOVERY_SENT -> "pedido desde la web";
                case LOCKED -> "";
                default -> "el paciente";
            });
        }
    }
}
