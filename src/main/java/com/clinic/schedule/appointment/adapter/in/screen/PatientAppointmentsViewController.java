package com.clinic.schedule.appointment.adapter.in.screen;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.appointment.application.usecase.ViewPatientAppointmentsUseCase;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The appointments of one patient, cancelled ones included. Reception only: the route is closed
 * to practitioners (D-34).
 *
 * <p>It lives in this module and not in {@code patient}, because appointments are this module's
 * data; the patients screen only links here. Throwaway like every screen (D-02).
 */
@Controller
class PatientAppointmentsViewController {

    private final ViewPatientAppointmentsUseCase patientAppointments;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    PatientAppointmentsViewController(ViewPatientAppointmentsUseCase patientAppointments, PatientReader patients,
            PractitionerReader practitioners) {
        this.patientAppointments = patientAppointments;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/agenda/patients/{id}")
    String appointments(@PathVariable UUID id, Model model, RedirectAttributes flash) {
        PatientSummary patient = patients.findAll(List.of(id)).stream().findFirst().orElse(null);
        if (patient == null) {
            flash.addFlashAttribute("error", "No se encontró el paciente.");
            return "redirect:/patients";
        }
        Map<UUID, String> practitionerNames = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        List<AgendaRow> rows = patientAppointments.handle(new PatientId(id)).stream()
                .map(appointment -> AgendaRow.of(appointment, patient,
                        practitionerNames.get(appointment.practitionerId().value())))
                .toList();

        model.addAttribute("patient", patient);
        model.addAttribute("rows", rows);
        return "patient-appointments";
    }
}
