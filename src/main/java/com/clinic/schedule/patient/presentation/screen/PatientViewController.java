package com.clinic.schedule.patient.presentation.screen;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IncompleteDocumentException;
import com.clinic.schedule.patient.entity.InvalidPatientDetailsException;
import com.clinic.schedule.patient.entity.Patient;
import com.clinic.schedule.patient.entity.UnverifiableIdentityException;
import com.clinic.schedule.patient.service.DuplicateDocumentException;
import com.clinic.schedule.patient.service.PatientNotFoundException;
import com.clinic.schedule.patient.service.PatientService;

/**
 * Reception's patient screens: find a patient, register one — with a name, or without one under
 * an alias — and correct who a patient is, with the history of those corrections (D-60), and what
 * else is known of them, with its own (D-73).
 *
 * <p>Thin and throwaway, as D-02 requires: it calls {@link PatientService}, the same the REST
 * controller calls, and holds no rule. Deleting {@code presentation/screen/} with its templates
 * removes the screens and nothing else (D-02).
 *
 * <p>Refusals come back as messages in Spanish; the API answers in English and translating
 * is the interface's job (D-13).
 */
@Controller
class PatientViewController {

    private final PatientService patients;

    PatientViewController(PatientService patients) {
        this.patients = patients;
    }

    @GetMapping("/patients")
    String patients(@RequestParam(required = false) String query, Model model) {
        model.addAttribute("query", query);
        model.addAttribute("patients", patients.searchPatients(query).stream().map(PatientEntry::of).toList());
        model.addAttribute("documentTypes", DocumentType.values());
        model.addAttribute("relations", DetailsEntry.relations());
        return "patients";
    }

    @PostMapping("/patients")
    String register(@RequestParam String fullName,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) String documentNumber,
            @RequestParam(defaultValue = "false") boolean documentSeen,
            @ModelAttribute DetailsForm details,
            RedirectAttributes flash) {
        try {
            Patient registered = patients.register(fullName, IdentityDocument.ofNullable(documentType, documentNumber),
                    documentSeen, details.toDetails());
            flash.addFlashAttribute("message", "Paciente registrado: " + registered.getFullName() + ".");
            return "redirect:/patients?query=" + URLEncoder.encode(registered.getFullName(), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", refusal(e));
        }
        return "redirect:/patients";
    }

    /** Nobody is turned away for lack of a name: the patient gets an alias, shown at once (D-60). */
    @PostMapping("/patients/unidentified")
    String registerUnidentified(RedirectAttributes flash) {
        Patient registered = patients.registerUnidentifiedPatient();
        flash.addFlashAttribute("message", "Paciente registrado sin datos: " + registered.getAlias() + ".");
        return "redirect:/patients/" + registered.getId();
    }

    @GetMapping("/patients/{id}")
    String patient(@PathVariable UUID id, Model model) {
        try {
            Patient patient = patients.byId(id);
            model.addAttribute("patient", PatientEntry.of(patient));
            model.addAttribute("details", DetailsEntry.of(patient.getDetails()));
            model.addAttribute("history", patients.identityHistory(id).stream().map(IdentityChangeEntry::of).toList());
            model.addAttribute("detailsHistory",
                    patients.detailsHistory(id).stream().map(DetailsChangeEntry::of).toList());
        } catch (PatientNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        model.addAttribute("documentTypes", DocumentType.values());
        model.addAttribute("relations", DetailsEntry.relations());
        return "patient";
    }

    @PostMapping("/patients/{id}")
    String changeIdentity(@PathVariable UUID id, @RequestParam String fullName,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) String documentNumber,
            @RequestParam(defaultValue = "false") boolean documentSeen,
            RedirectAttributes flash) {
        try {
            patients.changeIdentity(id, fullName, IdentityDocument.ofNullable(documentType, documentNumber), documentSeen);
            flash.addFlashAttribute("message", "Datos guardados.");
        } catch (PatientNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", refusal(e));
        }
        return "redirect:/patients/" + id;
    }

    /** What else is known of the patient; each detail that changed goes to its history (D-73). */
    @PostMapping("/patients/{id}/details")
    String changeDetails(@PathVariable UUID id, @ModelAttribute DetailsForm details, RedirectAttributes flash) {
        try {
            patients.changeDetails(id, details.toDetails());
            flash.addFlashAttribute("message", "Datos personales guardados.");
        } catch (PatientNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", refusal(e));
        }
        return "redirect:/patients/" + id;
    }

    /** What reception reads when the data is refused; anything else is not a refusal and goes on. */
    private static String refusal(RuntimeException e) {
        if (e instanceof DuplicateDocumentException) {
            return "Ese documento ya pertenece a otro paciente.";
        }
        if (e instanceof IncompleteDocumentException) {
            return "El documento necesita tipo y número, o ninguno de los dos.";
        }
        if (e instanceof UnverifiableIdentityException) {
            return "Para marcar que viste el documento, el paciente necesita su documento y su nombre real.";
        }
        if (e instanceof InvalidPatientDetailsException) {
            return "Revisá los datos personales: la fecha de nacimiento no puede ser futura ni de hace más de 130 años; "
                    + "el teléfono lleva de 8 a 15 dígitos; el email, la forma nombre@dominio.com; si el teléfono o el "
                    + "email son de otra persona, van con su nombre y su vínculo; y el número de afiliado y el plan van "
                    + "con el nombre de la obra social, de hasta 100 caracteres (el afiliado, hasta 30; el plan, hasta 50).";
        }
        if (e instanceof IllegalArgumentException) {
            return "Revisá los datos: el nombre lleva letras y hasta 150 caracteres; el DNI, 7 u 8 dígitos; LE y LC, de 6 a 8 dígitos; CI y pasaporte, solo letras y números.";
        }
        throw e;
    }
}
