package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordRefusalsUseCase;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The admins' screen of the refused attempts to reach clinical content (D-72): searched by patient,
 * user and days, it shows who, when, what they tried and whose record, when known.
 */
@Controller
class RecordRefusalViewController {

    private final ListRecordRefusalsUseCase refusals;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    RecordRefusalViewController(ListRecordRefusalsUseCase refusals, PatientReader patients,
            PractitionerReader practitioners) {
        this.refusals = refusals;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/record-refusals")
    String refusals(@RequestParam(required = false) String patient, @RequestParam(required = false) String user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        // The admins' header, which offers their screens alone (D-68).
        model.addAttribute("admin", true);
        model.addAttribute("patient", patient);
        model.addAttribute("user", user);

        Set<PatientId> matching = patient == null || patient.isBlank() ? null
                : patients.search(patient).stream().map(found -> new PatientId(found.id())).collect(Collectors.toSet());
        ListRecordRefusalsUseCase.Result result;
        try {
            result = refusals.handle(new ListRecordRefusalsUseCase.Command(matching, user, from, to));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "El primer día es posterior al último.");
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            model.addAttribute("rows", List.of());
            return "record-refusals";
        }
        Map<UUID, PatientSummary> patientsById = patients.findAll(result.refusals().stream()
                        .flatMap(refusal -> refusal.patientId().stream())
                        .map(PatientId::value)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));

        model.addAttribute("from", result.from());
        model.addAttribute("to", result.to());
        model.addAttribute("limitReached", result.limitReached());
        model.addAttribute("rows", result.refusals().stream()
                .map(refusal -> RefusalRow.of(refusal, patientsById, names))
                .toList());
        return "record-refusals";
    }

    /** One refused attempt as the screen lists it, in Spanish (D-13). */
    record RefusalRow(String when, String user, String practitioner, String patient, String what) {

        static RefusalRow of(RecordRefusal refusal, Map<UUID, PatientSummary> patients, Map<UUID, String> names) {
            String patientLabel = refusal.patientId()
                    .map(id -> {
                        PatientSummary patient = patients.get(id.value());
                        return patient == null ? "(paciente desconocido)"
                                : patient.document() == null ? patient.fullName()
                                : patient.fullName() + " — " + patient.document();
                    })
                    .orElse("—");
            String what = switch (refusal.attempt()) {
                case RECORD -> "Historia";
                case ATTACHMENT -> "Archivo adjunto";
                case ROUTE -> "Ruta " + refusal.route().orElse("");
            };
            return new RefusalRow(ClinicalRecordEntry.format(refusal.refusedAt()), refusal.refusedBy(),
                    refusal.practitionerId().map(id -> ClinicalRecordEntry.nameOf(names, id.value())).orElse("—"),
                    patientLabel, what);
        }
    }
}
