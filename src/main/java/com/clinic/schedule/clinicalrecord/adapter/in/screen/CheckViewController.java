package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckResultUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckUseCase;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;

/**
 * The practitioner's actions on checks from the clinical record screen: a check done without a
 * consultation, and the result of a pending one (D-53). Both go back to the record; a refusal
 * comes back with what was typed, and a user who may not read the record gets a 403.
 */
@Controller
class CheckViewController {

    private final RecordCheckUseCase recordCheck;
    private final RecordCheckResultUseCase recordResult;

    CheckViewController(RecordCheckUseCase recordCheck, RecordCheckResultUseCase recordResult) {
        this.recordCheck = recordCheck;
        this.recordResult = recordResult;
    }

    @PostMapping("/checks")
    String recordCheck(@RequestParam UUID patientId, @RequestParam(required = false) String description,
            @RequestParam(required = false) String result, RedirectAttributes flash) {
        String problem = null;
        if (description == null || description.isBlank()) {
            problem = "Escribí qué chequeo se hizo.";
        } else {
            try {
                recordCheck.handle(new RecordCheckUseCase.Command(CheckId.newId(), new PatientId(patientId),
                        description, result));
            } catch (ClinicalRecordAccessDeniedException e) {
                if (!e.mayRead()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
                }
                problem = "El chequeo se registra cuando el paciente llegó a atenderse con vos hoy.";
            } catch (IllegalArgumentException e) {
                problem = "Revisá el chequeo: qué se hizo tiene hasta 2 000 caracteres, y el resultado hasta 10 000.";
            }
        }
        if (problem != null) {
            flash.addFlashAttribute("error", problem);
            flash.addFlashAttribute("checkDescription", description);
            flash.addFlashAttribute("checkResult", result);
        } else {
            flash.addFlashAttribute("message", "Chequeo registrado.");
        }
        return "redirect:/clinical-records/" + patientId;
    }

    @PostMapping("/checks/{id}/result")
    String recordResult(@PathVariable UUID id, @RequestParam UUID patientId,
            @RequestParam(required = false) String text, RedirectAttributes flash) {
        String problem = null;
        if (text == null || text.isBlank()) {
            problem = "Escribí el resultado.";
        } else {
            try {
                recordResult.handle(new RecordCheckResultUseCase.Command(new CheckId(id), text));
            } catch (ClinicalRecordAccessDeniedException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            } catch (ResultAlreadyRecordedException e) {
                problem = "Ese chequeo ya tiene resultado: se cargó recién.";
            } catch (IllegalArgumentException e) {
                problem = "El resultado tiene hasta 10 000 caracteres.";
            }
        }
        if (problem != null) {
            flash.addFlashAttribute("error", problem);
            flash.addFlashAttribute("resultFor", id);
            flash.addFlashAttribute("resultText", text);
        } else {
            flash.addFlashAttribute("message", "Resultado cargado.");
        }
        return "redirect:/clinical-records/" + patientId;
    }
}
