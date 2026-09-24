package com.clinic.schedule.patient.presentation.screen;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IncompleteDocumentException;
import com.clinic.schedule.patient.entity.Patient;
import com.clinic.schedule.patient.service.DuplicateDocumentException;
import com.clinic.schedule.patient.service.PatientService;

/**
 * Reception's patient screen: find a patient, or register one before booking.
 *
 * <p>Thin and throwaway, as D-02 requires: it calls {@link PatientService}, the same the REST
 * controller calls, and holds no rule. Deleting {@code presentation/screen/} with its template
 * removes the screen and nothing else (D-02).
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
        model.addAttribute("patients", patients.search(query));
        model.addAttribute("documentTypes", DocumentType.values());
        return "patients";
    }

    @PostMapping("/patients")
    String register(@RequestParam String fullName,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) String documentNumber,
            RedirectAttributes flash) {
        try {
            Patient registered = patients.register(fullName, IdentityDocument.ofNullable(documentType, documentNumber));
            flash.addFlashAttribute("message", "Paciente registrado: " + registered.getFullName() + ".");
            return "redirect:/patients?query=" + java.net.URLEncoder.encode(registered.getFullName(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (DuplicateDocumentException e) {
            flash.addFlashAttribute("error", "Ese documento ya pertenece a otro paciente.");
        } catch (IncompleteDocumentException e) {
            flash.addFlashAttribute("error", "El documento necesita tipo y número, o ninguno de los dos.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", "Revisá los datos: el nombre lleva letras y hasta 150 caracteres; el DNI, 7 u 8 dígitos; LE y LC, de 6 a 8 dígitos; CI y pasaporte, solo letras y números.");
        }
        return "redirect:/patients";
    }
}
