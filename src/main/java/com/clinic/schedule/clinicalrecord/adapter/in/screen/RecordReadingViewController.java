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

import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordReadingsUseCase;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.ReadAccess;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The admins' screen of who read which clinical record (D-71): searched by patient, practitioner and
 * days, it shows who, whose, when, by which access and whether it was a file — never which file, nor
 * anything that was read.
 */
@Controller
class RecordReadingViewController {

    private final ListRecordReadingsUseCase readings;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    RecordReadingViewController(ListRecordReadingsUseCase readings, PatientReader patients,
            PractitionerReader practitioners) {
        this.readings = readings;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/record-readings")
    String readings(@RequestParam(required = false) String patient,
            @RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        // The admins' header, which offers their screens alone (D-68).
        model.addAttribute("admin", true);
        model.addAttribute("patient", patient);
        model.addAttribute("practitionerId", practitionerId);
        model.addAttribute("practitioners", practitioners.findAll());
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));

        Set<PatientId> matching = patient == null || patient.isBlank() ? null
                : patients.search(patient).stream().map(found -> new PatientId(found.id())).collect(Collectors.toSet());
        ListRecordReadingsUseCase.Result result;
        try {
            result = readings.handle(new ListRecordReadingsUseCase.Command(matching,
                    practitionerId == null ? null : new PractitionerId(practitionerId), from, to));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "El primer día es posterior al último.");
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            model.addAttribute("rows", List.of());
            return "record-readings";
        }
        Map<UUID, PatientSummary> patientsById = patients.findAll(result.readings().stream()
                        .map(reading -> reading.patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));

        model.addAttribute("from", result.from());
        model.addAttribute("to", result.to());
        model.addAttribute("limitReached", result.limitReached());
        model.addAttribute("rows", result.readings().stream()
                .map(reading -> ReadingRow.of(reading, patientsById, names))
                .toList());
        return "record-readings";
    }

    /** One reading as the screen lists it, in Spanish (D-13). */
    record ReadingRow(String when, String practitioner, String user, String patient, String access, String what) {

        static ReadingRow of(RecordReading reading, Map<UUID, PatientSummary> patients, Map<UUID, String> names) {
            PatientSummary patient = patients.get(reading.patientId().value());
            String patientLabel = patient == null ? "(paciente desconocido)"
                    : patient.document() == null ? patient.fullName()
                    : patient.fullName() + " — " + patient.document();
            return new ReadingRow(ClinicalRecordEntry.format(reading.readAt()),
                    reading.practitionerId().map(id -> ClinicalRecordEntry.nameOf(names, id.value())).orElse("—"),
                    reading.readBy().orElse("sin usuario registrado"),
                    patientLabel,
                    access(reading.access()),
                    reading.attachmentId().isPresent() ? "Archivo adjunto"
                            : reading.recordCopyId().isPresent() ? "Copia de la historia" : "Historia");
        }

        private static String access(ReadAccess access) {
            return switch (access) {
                case VISIT_TODAY -> "Lo atiende hoy";
                case ACCESS_GRANT -> "Acceso pedido";
                case SEEN_WITHIN_PERIOD -> "Lo atendió dentro del período";
                case COPY_REQUEST -> "Copia pedida por el paciente";
            };
        }
    }
}
