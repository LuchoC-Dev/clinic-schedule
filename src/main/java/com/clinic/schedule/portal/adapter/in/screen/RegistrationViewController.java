package com.clinic.schedule.portal.adapter.in.screen;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.application.exception.ChildRequestsExceededException;
import com.clinic.schedule.portal.application.exception.FamilyGroupNotFoundException;
import com.clinic.schedule.portal.application.exception.InvalidRegistrationException;
import com.clinic.schedule.portal.application.usecase.RegisterChildUseCase;
import com.clinic.schedule.portal.application.usecase.RegisterOnWebUseCase;
import com.clinic.schedule.portal.domain.FamilyMember;
import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;
import com.clinic.schedule.portal.domain.exception.InvalidAccountEmailException;
import com.clinic.schedule.shared.PasswordPolicy;

/**
 * The screens of registering on the web (D-91), in Spanish, since patients read them (D-13): with nobody logged
 * in, the form — the adult, and up to {@value #CHILD_SLOTS} children under 13 (D-92) —, the page that says an email
 * went out — the same whether a document was known or not —; the email's link opens the patients' application,
 * whose button confirms it (D-110). Logged in,
 * the adult registers a child from *Mi familia*. Thin and throwaway, as every screen (D-02).
 */
@Controller
class RegistrationViewController {

    /** How many children the form offers to register with the adult; the API takes up to five. */
    static final int CHILD_SLOTS = 3;

    private final RegisterOnWebUseCase registration;
    private final RegisterChildUseCase child;
    private final PatientReader patients;

    RegistrationViewController(RegisterOnWebUseCase registration, RegisterChildUseCase child,
            PatientReader patients) {
        this.registration = registration;
        this.child = child;
        this.patients = patients;
    }

    /** A child's block of the form, as typed, to give it back on a refusal. */
    record ChildForm(String fullName, String documentType, String documentNumber, String birthDate,
            String relation) {

        boolean typed() {
            return !(blank(fullName) && blank(documentNumber) && blank(birthDate));
        }
    }

    @GetMapping("/portal/register")
    String form(Model model) {
        model.addAttribute("documentTypes", patients.documentTypes());
        List<ChildForm> empty = new ArrayList<>();
        for (int i = 0; i < CHILD_SLOTS; i++) {
            empty.add(new ChildForm(null, null, null, null, null));
        }
        model.addAttribute("children", empty);
        return "portal-register";
    }

    /**
     * The password is asked twice, as on activating an account. What was typed comes back on a refusal. A child's
     * block left empty is no child.
     */
    @PostMapping("/portal/register")
    String register(@RequestParam String fullName, @RequestParam String documentType,
            @RequestParam String documentNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate,
            @RequestParam(required = false) String phone, @RequestParam String email, @RequestParam String password,
            @RequestParam String repeatedPassword,
            @RequestParam(name = "childFullName", required = false) List<String> childFullNames,
            @RequestParam(name = "childDocumentType", required = false) List<String> childDocumentTypes,
            @RequestParam(name = "childDocumentNumber", required = false) List<String> childDocumentNumbers,
            @RequestParam(name = "childBirthDate", required = false) List<String> childBirthDates,
            @RequestParam(name = "childRelation", required = false) List<String> childRelations,
            Model model, RedirectAttributes redirect) {
        List<ChildForm> forms = new ArrayList<>();
        for (int i = 0; i < CHILD_SLOTS; i++) {
            forms.add(new ChildForm(at(childFullNames, i), at(childDocumentTypes, i), at(childDocumentNumbers, i),
                    at(childBirthDates, i), at(childRelations, i)));
        }
        model.addAttribute("documentTypes", patients.documentTypes());
        model.addAttribute("fullName", fullName);
        model.addAttribute("documentType", documentType);
        model.addAttribute("documentNumber", documentNumber);
        model.addAttribute("birthDate", birthDate);
        model.addAttribute("phone", phone);
        model.addAttribute("email", email);
        model.addAttribute("children", forms);
        if (!password.equals(repeatedPassword)) {
            model.addAttribute("error", "Las dos contraseñas no coinciden.");
            return "portal-register";
        }
        List<RegisterOnWebUseCase.ChildCommand> children = new ArrayList<>();
        for (ChildForm form : forms) {
            if (form.typed()) {
                children.add(new RegisterOnWebUseCase.ChildCommand(form.fullName(), form.documentType(),
                        form.documentNumber(), date(form.birthDate()),
                        blank(form.relation()) ? null : FamilyMember.Relation.valueOf(form.relation())));
            }
        }
        boolean sent;
        try {
            sent = registration.request(new RegisterOnWebUseCase.Command(fullName, documentType, documentNumber,
                    birthDate, phone, email, password, children));
        } catch (InvalidRegistrationException e) {
            model.addAttribute("error", "Revisá los datos: los nombres, los documentos, las fechas de nacimiento y el "
                    + "teléfono, si lo pusiste.");
            return "portal-register";
        } catch (AccountRefusedException e) {
            model.addAttribute("error", "Desde la web se registran mayores de 18 años. Si sos menor, tu mamá, tu "
                    + "papá o quien te cuide puede registrarte en recepción.");
            return "portal-register";
        } catch (FamilyRefusedException e) {
            model.addAttribute("error", "Desde la web se registran hijos menores de 13. Desde los 13, se registran en "
                    + "recepción.");
            return "portal-register";
        } catch (InvalidAccountEmailException e) {
            model.addAttribute("error", "El email tiene la forma nombre@dominio.com y hasta 254 caracteres.");
            return "portal-register";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", refusal(birthDate, children, password));
            return "portal-register";
        }
        if (!sent) {
            model.addAttribute("error", "No pudimos mandarte el email. Probá de nuevo en un rato.");
            return "portal-register";
        }
        redirect.addFlashAttribute("email", email.strip());
        return "redirect:/portal/register/sent";
    }

    /** What the form missed, among what the rules refuse as malformed. */
    private static String refusal(LocalDate birthDate, List<RegisterOnWebUseCase.ChildCommand> children,
            String password) {
        if (birthDate == null) {
            return "Falta tu fecha de nacimiento.";
        }
        if (children.stream().anyMatch(child -> child.birthDate() == null)) {
            return "Falta la fecha de nacimiento de uno de tus hijos.";
        }
        try {
            PasswordPolicy.check(password);
        } catch (IllegalArgumentException e) {
            return "La contraseña tiene de 10 a 72 caracteres.";
        }
        return "Pusiste el mismo documento dos veces.";
    }

    @GetMapping("/portal/register/sent")
    String sent() {
        return "portal-register-sent";
    }

    /** The address of the registration's email, sent on to the patients' application (D-110). */
    @GetMapping("/portal/register/confirm")
    String confirmation(@RequestParam(required = false) String token) {
        return PortalViewController.toApplication("/register/confirm", token);
    }

    /**
     * The adult logged in registers a child from *Mi familia* (D-92). The same answer whether the practice knew the
     * document or not.
     */
    @PostMapping("/portal/family/children")
    String registerChild(@RequestParam String fullName, @RequestParam String documentType,
            @RequestParam String documentNumber, @RequestParam(required = false) String birthDate,
            @RequestParam FamilyMember.Relation relation, @RequestParam(required = false) UUID groupId,
            RedirectAttributes redirect) {
        try {
            child.handle(new RegisterChildUseCase.Command(fullName, documentType, documentNumber, date(birthDate),
                    relation, groupId));
            redirect.addFlashAttribute("message", "Recibimos el pedido. Si " + fullName.strip() + " no aparece en tu "
                    + "familia, acercate a recepción con su documento.");
        } catch (InvalidRegistrationException e) {
            redirect.addFlashAttribute("error", "Revisá sus datos: el nombre, el documento y la fecha de nacimiento.");
        } catch (FamilyRefusedException e) {
            redirect.addFlashAttribute("error", "Desde la web registrás a tus hijos menores de 13, en un grupo tuyo.");
        } catch (FamilyGroupNotFoundException e) {
            redirect.addFlashAttribute("error", "Ese grupo ya no está.");
        } catch (ChildRequestsExceededException e) {
            redirect.addFlashAttribute("error", "Por hoy ya pediste registrar a varios hijos: probá mañana, o "
                    + "acercate a recepción.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Falta su fecha de nacimiento.");
        }
        return "redirect:/portal/family";
    }

    private static String at(List<String> values, int i) {
        return values == null || i >= values.size() ? null : values.get(i);
    }

    private static LocalDate date(String text) {
        if (blank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
