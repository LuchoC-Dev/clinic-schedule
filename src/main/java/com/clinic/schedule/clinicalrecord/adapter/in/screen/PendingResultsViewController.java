package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.clinicalrecord.application.exception.CheckNotFoundException;
import com.clinic.schedule.clinicalrecord.application.usecase.ListPendingChecksUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RequestAccessUseCase;
import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.AccessGrantPolicy;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.exception.CheckNotPendingException;
import com.clinic.schedule.clinicalrecord.domain.exception.ReasonRequiredException;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The practitioner's list of results to report: every check of the practice waiting for one,
 * showing only the patient and the check, with "Ver historia" for a record the user may read and
 * "Pedir acceso" for one they may not (D-54). Asking is granted at once, for now, and leads to the
 * record.
 */
@Controller
class PendingResultsViewController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ListPendingChecksUseCase pendingChecks;
    private final RequestAccessUseCase requestAccess;
    private final AccessGrantPolicy grantPolicy;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    PendingResultsViewController(ListPendingChecksUseCase pendingChecks, RequestAccessUseCase requestAccess,
            AccessGrantPolicy grantPolicy, PatientReader patients, PractitionerReader practitioners) {
        this.pendingChecks = pendingChecks;
        this.requestAccess = requestAccess;
        this.grantPolicy = grantPolicy;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/checks/pending")
    String pending(Model model) {
        List<ListPendingChecksUseCase.PendingCheck> found = pendingChecks.handle();
        Map<UUID, PatientSummary> patientsById = patients.findAll(found.stream()
                        .map(pending -> pending.check().patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));

        model.addAttribute("rows", found.stream()
                .map(pending -> PendingRow.of(pending, patientsById, names))
                .toList());
        model.addAttribute("reasonRequired", grantPolicy.isReasonRequired());
        // The header shows a practitioner's links (D-34).
        model.addAttribute("readOnly", true);
        return "pending-results";
    }

    /**
     * Asks for access to report the check. Granted, it leads to the patient's record; refused, it
     * comes back to the list with the reason typed.
     */
    @PostMapping("/checks/{id}/access-grants")
    String requestAccess(@PathVariable UUID id, @RequestParam(required = false) String reason,
            RedirectAttributes flash) {
        String problem;
        try {
            AccessGrant grant = requestAccess.handle(new RequestAccessUseCase.Command(new CheckId(id), reason));
            flash.addFlashAttribute("message", "Acceso autorizado hasta el " + grant.validUntil().format(DAY) + ".");
            return "redirect:/clinical-records/" + grant.patientId().value();
        } catch (ReasonRequiredException e) {
            problem = "Escribí el motivo del pedido de acceso.";
        } catch (CheckNotPendingException e) {
            problem = "Ese chequeo ya tiene resultado: no hace falta informarlo.";
        } catch (CheckNotFoundException e) {
            problem = "Ese chequeo no existe.";
        } catch (IllegalArgumentException e) {
            problem = "El motivo tiene hasta 500 caracteres.";
        }
        flash.addFlashAttribute("error", problem);
        flash.addFlashAttribute("reasonFor", id);
        flash.addFlashAttribute("reasonText", reason);
        return "redirect:/checks/pending";
    }

    /** One pending check as the list shows it. */
    record PendingRow(UUID checkId, UUID patientId, String patient, String description, String performedAt,
            String performedBy, boolean mayRead) {

        static PendingRow of(ListPendingChecksUseCase.PendingCheck pending, Map<UUID, PatientSummary> patients,
                Map<UUID, String> names) {
            PatientSummary patient = patients.get(pending.check().patientId().value());
            String patientLabel = patient == null ? "(paciente desconocido)"
                    : patient.document() == null ? patient.fullName()
                    : patient.fullName() + " — " + patient.document();
            return new PendingRow(
                    pending.check().id().value(),
                    pending.check().patientId().value(),
                    patientLabel,
                    pending.check().description(),
                    ClinicalRecordEntry.format(pending.check().performedAt()),
                    ClinicalRecordEntry.nameOf(names, pending.check().performedBy().value()),
                    pending.mayRead());
        }
    }
}
