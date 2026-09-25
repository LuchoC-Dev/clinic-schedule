package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.ConsultationChangedException;
import com.clinic.schedule.clinicalrecord.application.exception.UnknownOrderException;
import com.clinic.schedule.clinicalrecord.application.usecase.AddAddendumUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordConsultationUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ViewClinicalRecordUseCase;
import com.clinic.schedule.clinicalrecord.domain.AddendumId;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.ItemKind;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The practitioner's screens over the clinical record: reading a patient's record and adding
 * addenda to it, and the consultation view — the form beside the record (D-51, D-52).
 *
 * <p>Thin and throwaway, as D-02 requires: it calls the same use cases as the REST controller
 * and holds no rule. A user who may not read the record gets a 403, never an empty page (D-50).
 * The patient's and practitioners' names come from their modules' api, because only the screen
 * needs them (D-33).
 */
@Controller
class ConsultationViewController {

    private static final String NOT_ARRIVED =
            "La consulta se registra cuando el paciente llegó a atenderse con vos hoy.";

    private static final String INVALID = "Revisá la consulta: lo consultado tiene hasta 10 000 caracteres, "
            + "cada ítem y cada chequeo hasta 2 000, cada resultado hasta 10 000, y son 50 de cada uno como máximo.";

    private final ViewClinicalRecordUseCase clinicalRecord;
    private final RecordConsultationUseCase record;
    private final AddAddendumUseCase addAddendum;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    ConsultationViewController(ViewClinicalRecordUseCase clinicalRecord, RecordConsultationUseCase record,
            AddAddendumUseCase addAddendum, PatientReader patients, PractitionerReader practitioners) {
        this.clinicalRecord = clinicalRecord;
        this.record = record;
        this.addAddendum = addAddendum;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    /**
     * A patient's clinical record, to read — before the patient arrives, for instance — and to
     * add addenda to its consultations (D-52).
     */
    @GetMapping("/clinical-records/{patientId}")
    String clinicalRecord(@PathVariable UUID patientId, Model model) {
        ViewClinicalRecordUseCase.ClinicalRecord read = readOrRefuse(patientId);
        addRecord(model, patientId, read);
        model.addAttribute("recordActions", true);
        return "clinical-record";
    }

    /**
     * Adds an addendum to a consultation, and goes back to the record. A refusal comes back with
     * the text typed, next to the consultation it was for; a user who may not read the record
     * gets a 403.
     */
    @PostMapping("/consultations/{id}/addenda")
    String addAddendum(@PathVariable UUID id, @RequestParam UUID patientId,
            @RequestParam(required = false) String text, RedirectAttributes flash) {
        String backToRecord = "redirect:/clinical-records/" + patientId;
        String problem = null;
        if (text == null || text.isBlank()) {
            problem = "Escribí la aclaración.";
        } else {
            try {
                addAddendum.handle(new AddAddendumUseCase.Command(new ConsultationId(id), AddendumId.newId(), text));
            } catch (ClinicalRecordAccessDeniedException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            } catch (ConsultationChangedException e) {
                problem = "Otro profesional agregó una aclaración a esta consulta recién: revisala y volvé a enviar la tuya.";
            } catch (IllegalArgumentException e) {
                problem = "La aclaración tiene hasta 2 000 caracteres.";
            }
        }
        if (problem != null) {
            flash.addFlashAttribute("error", problem);
            flash.addFlashAttribute("addendumFor", id);
            flash.addFlashAttribute("addendumText", text);
            return backToRecord;
        }
        flash.addFlashAttribute("message", "Aclaración agregada.");
        return backToRecord;
    }

    /**
     * The consultation view: the form beside the record. Only when the consultation would be
     * accepted — the patient arrived to be seen by this practitioner today; otherwise it falls
     * back to the record, saying why.
     *
     * <p>A form refused before comes back as a flash attribute, with everything typed in it.
     */
    @GetMapping("/consultations/new")
    String newConsultation(@RequestParam UUID patientId, Model model, RedirectAttributes flash) {
        ViewClinicalRecordUseCase.ClinicalRecord read = readOrRefuse(patientId);
        if (!read.mayRecord()) {
            flash.addFlashAttribute("error", NOT_ARRIVED);
            return "redirect:/clinical-records/" + patientId;
        }
        ConsultationForm form = model.containsAttribute("form")
                ? (ConsultationForm) model.getAttribute("form")
                : ConsultationForm.empty(patientId);
        addRecord(model, patientId, read);
        model.addAttribute("form", form);
        model.addAttribute("rows", form.itemRowsToShow());
        model.addAttribute("checkRows", form.checkRowsToShow());
        model.addAttribute("orderRows", form.orderRowsToShow());
        model.addAttribute("kinds", Arrays.stream(ItemKind.values())
                .collect(Collectors.toMap(ItemKind::name, ClinicalRecordEntry::labelOf,
                        (first, second) -> first, LinkedHashMap::new)));
        return "consultation-new";
    }

    /**
     * Sends the consultation, or, with "Más renglones", only brings the form back with more rows.
     * A refusal returns to the form with everything typed; a user who may not even read the
     * record gets a 403.
     */
    @PostMapping("/consultations")
    String send(@ModelAttribute ConsultationForm form, @RequestParam(defaultValue = "send") String action,
            RedirectAttributes flash) {
        if (form.patientId() == null) {
            return "redirect:/agenda";
        }
        String backToForm = "redirect:/consultations/new?patientId=" + form.patientId();
        if ("more".equals(action)) {
            flash.addFlashAttribute("form", form.withMoreRows());
            return backToForm;
        }
        String problem = problemWith(form);
        if (problem != null) {
            flash.addFlashAttribute("form", form);
            flash.addFlashAttribute("error", problem);
            return backToForm;
        }
        try {
            record.handle(new RecordConsultationUseCase.Command(ConsultationId.newId(),
                    new PatientId(form.patientId()), form.notes(), form.items(), form.checks(), form.orders()));
        } catch (ClinicalRecordAccessDeniedException e) {
            if (!e.mayRead()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            }
            flash.addFlashAttribute("form", form);
            flash.addFlashAttribute("error", NOT_ARRIVED);
            return backToForm;
        } catch (UnknownOrderException | OrderNotOpenException e) {
            flash.addFlashAttribute("form", form);
            flash.addFlashAttribute("error", "Una de las órdenes elegidas ya no está abierta: revisá los chequeos.");
            return backToForm;
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("form", form);
            flash.addFlashAttribute("error", INVALID);
            return backToForm;
        }
        flash.addFlashAttribute("message", "Consulta registrada.");
        return "redirect:/clinical-records/" + form.patientId();
    }

    /** What the screen can tell before sending: the rules proper are the domain's. */
    private static String problemWith(ConsultationForm form) {
        if (form.notes() == null || form.notes().isBlank()) {
            return "Escribí lo consultado.";
        }
        if (form.hasHalfFilledRow()) {
            return "Cada ítem necesita su tipo y su texto, y cada chequeo qué se hizo o la orden que cumple: completá o vaciá los renglones a medias.";
        }
        return null;
    }

    private ViewClinicalRecordUseCase.ClinicalRecord readOrRefuse(UUID patientId) {
        try {
            return clinicalRecord.handle(new PatientId(patientId));
        } catch (ClinicalRecordAccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    private void addRecord(Model model, UUID patientId, ViewClinicalRecordUseCase.ClinicalRecord read) {
        PatientSummary patient = patients.findAll(List.of(patientId)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        model.addAttribute("patient", patient);
        model.addAttribute("entries", ClinicalRecordEntry.of(read, names));
        model.addAttribute("grants", GrantEntry.of(read, names));
        // The orders a check done now may fulfil (D-55).
        model.addAttribute("openOrders", read.orders().stream().filter(CheckOrder::isOpen).toList());
        model.addAttribute("mayRecord", read.mayRecord());
        // The header offers a practitioner their agenda only (D-34).
        model.addAttribute("readOnly", true);
    }
}
