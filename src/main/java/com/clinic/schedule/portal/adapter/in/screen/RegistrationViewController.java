package com.clinic.schedule.portal.adapter.in.screen;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.application.exception.InvalidRegistrationException;
import com.clinic.schedule.portal.application.exception.InvalidRegistrationLinkException;
import com.clinic.schedule.portal.application.usecase.RegisterOnWebUseCase;
import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.InvalidAccountEmailException;

/**
 * The screens of registering on the web (D-91), in Spanish, since patients read them (D-13), with nobody logged
 * in: the form, the page that says an email went out — the same whether the document was known or not —, and the
 * page of the email's link, whose button confirms it. Opening the link confirms nothing by itself, since mail
 * readers open links on their own. Thin and throwaway, as every screen (D-02).
 */
@Controller
class RegistrationViewController {

    private final RegisterOnWebUseCase registration;
    private final PatientReader patients;

    RegistrationViewController(RegisterOnWebUseCase registration, PatientReader patients) {
        this.registration = registration;
        this.patients = patients;
    }

    @GetMapping("/portal/register")
    String form(Model model) {
        model.addAttribute("documentTypes", patients.documentTypes());
        return "portal-register";
    }

    /** The password is asked twice, as on activating an account. What was typed comes back on a refusal. */
    @PostMapping("/portal/register")
    String register(@RequestParam String fullName, @RequestParam String documentType,
            @RequestParam String documentNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate,
            @RequestParam(required = false) String phone, @RequestParam String email, @RequestParam String password,
            @RequestParam String repeatedPassword, Model model, RedirectAttributes redirect) {
        model.addAttribute("documentTypes", patients.documentTypes());
        model.addAttribute("fullName", fullName);
        model.addAttribute("documentType", documentType);
        model.addAttribute("documentNumber", documentNumber);
        model.addAttribute("birthDate", birthDate);
        model.addAttribute("phone", phone);
        model.addAttribute("email", email);
        if (!password.equals(repeatedPassword)) {
            model.addAttribute("error", "Las dos contraseñas no coinciden.");
            return "portal-register";
        }
        boolean sent;
        try {
            sent = registration.request(new RegisterOnWebUseCase.Command(fullName, documentType, documentNumber,
                    birthDate, phone, email, password));
        } catch (InvalidRegistrationException e) {
            model.addAttribute("error", "Revisá tus datos: el nombre, el documento, la fecha de nacimiento y el "
                    + "teléfono, si lo pusiste.");
            return "portal-register";
        } catch (AccountRefusedException e) {
            model.addAttribute("error", "Desde la web se registran mayores de 18 años. Si sos menor, tu mamá, tu "
                    + "papá o quien te cuide puede registrarte en recepción.");
            return "portal-register";
        } catch (InvalidAccountEmailException e) {
            model.addAttribute("error", "El email tiene la forma nombre@dominio.com y hasta 254 caracteres.");
            return "portal-register";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", birthDate == null ? "Falta tu fecha de nacimiento."
                    : "La contraseña tiene de 10 a 72 caracteres.");
            return "portal-register";
        }
        if (!sent) {
            model.addAttribute("error", "No pudimos mandarte el email. Probá de nuevo en un rato.");
            return "portal-register";
        }
        redirect.addFlashAttribute("email", email.strip());
        return "redirect:/portal/register/sent";
    }

    @GetMapping("/portal/register/sent")
    String sent() {
        return "portal-register-sent";
    }

    @GetMapping("/portal/register/confirm")
    String confirmation(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("usable", registration.isUsable(token));
        return "portal-register-confirm";
    }

    @PostMapping("/portal/register/confirm")
    String confirm(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        try {
            if (registration.confirm(token) == RegisterOnWebUseCase.Confirmation.REGISTERED) {
                return "redirect:/portal/login?registered";
            }
            model.addAttribute("usable", false);
            model.addAttribute("known", true);
        } catch (InvalidRegistrationLinkException e) {
            model.addAttribute("usable", false);
        }
        return "portal-register-confirm";
    }
}
