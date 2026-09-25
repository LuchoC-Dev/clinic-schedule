package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.clinicalrecord.application.exception.CheckChangedException;
import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.usecase.AddAttachmentUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.AddCheckAddendumUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckResultUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckUseCase;
import com.clinic.schedule.clinicalrecord.application.exception.UnknownOrderException;
import com.clinic.schedule.clinicalrecord.domain.AddendumId;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.exception.FileTooLargeException;
import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;
import com.clinic.schedule.clinicalrecord.domain.exception.UnsupportedFileException;

/**
 * The practitioner's actions on checks from the clinical record screen: a check done without a
 * consultation, and the result of a pending one (D-53). Both go back to the record; a refusal
 * comes back with what was typed, and a user who may not read the record gets a 403.
 */
@Controller
class CheckViewController {

    private final RecordCheckUseCase recordCheck;
    private final RecordCheckResultUseCase recordResult;
    private final AddAttachmentUseCase addAttachment;
    private final AddCheckAddendumUseCase addAddendum;

    CheckViewController(RecordCheckUseCase recordCheck, RecordCheckResultUseCase recordResult,
            AddAttachmentUseCase addAttachment, AddCheckAddendumUseCase addAddendum) {
        this.recordCheck = recordCheck;
        this.recordResult = recordResult;
        this.addAttachment = addAttachment;
        this.addAddendum = addAddendum;
    }

    @PostMapping("/checks")
    String recordCheck(@RequestParam UUID patientId, @RequestParam(required = false) String description,
            @RequestParam(required = false) String result, @RequestParam(required = false) UUID orderId,
            RedirectAttributes flash) {
        String problem = null;
        if ((description == null || description.isBlank()) && orderId == null) {
            problem = "Escribí qué chequeo se hizo, o elegí la orden que cumple.";
        } else {
            try {
                recordCheck.handle(new RecordCheckUseCase.Command(CheckId.newId(), new PatientId(patientId),
                        description, result, orderId == null ? null : new CheckOrderId(orderId)));
            } catch (ClinicalRecordAccessDeniedException e) {
                if (!e.mayRead()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
                }
                problem = "El chequeo se registra cuando el paciente llegó a atenderse con vos hoy.";
            } catch (UnknownOrderException | OrderNotOpenException e) {
                problem = "Esa orden ya no está abierta.";
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

    /**
     * The result of a pending check, in words, in a file, or both: the file is attached first, and
     * the result may then leave its text out (D-57).
     */
    /**
     * Adds an addendum to a check, and goes back to the record: how a wrong result is corrected
     * (D-58). A refusal comes back with the text typed; a user who may not read the record gets a 403.
     */
    @PostMapping("/checks/{id}/addenda")
    String addAddendum(@PathVariable UUID id, @RequestParam UUID patientId,
            @RequestParam(required = false) String text, RedirectAttributes flash) {
        String problem = null;
        if (text == null || text.isBlank()) {
            problem = "Escribí la aclaración.";
        } else {
            try {
                addAddendum.handle(new AddCheckAddendumUseCase.Command(new CheckId(id), AddendumId.newId(), text));
            } catch (ClinicalRecordAccessDeniedException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            } catch (CheckChangedException e) {
                problem = "Otro profesional cambió este chequeo recién: revisalo y volvé a enviar tu aclaración.";
            } catch (IllegalArgumentException e) {
                problem = "La aclaración tiene hasta 2 000 caracteres.";
            }
        }
        if (problem != null) {
            flash.addFlashAttribute("error", problem);
            flash.addFlashAttribute("addendumFor", id);
            flash.addFlashAttribute("addendumText", text);
        } else {
            flash.addFlashAttribute("message", "Aclaración agregada.");
        }
        return "redirect:/clinical-records/" + patientId;
    }

    @PostMapping("/checks/{id}/result")
    String recordResult(@PathVariable UUID id, @RequestParam UUID patientId,
            @RequestParam(required = false) String text, @RequestParam(required = false) MultipartFile file,
            RedirectAttributes flash) throws IOException {
        boolean withFile = file != null && !file.isEmpty();
        String problem = null;
        if ((text == null || text.isBlank()) && !withFile) {
            problem = "Escribí el resultado o adjuntá el archivo que lo trae.";
        } else {
            try {
                if (withFile) {
                    addAttachment.handle(new AddAttachmentUseCase.Command(new AttachmentOwner.OfCheck(new CheckId(id)),
                            file.getOriginalFilename(), file.getBytes()));
                }
                recordResult.handle(new RecordCheckResultUseCase.Command(new CheckId(id), text));
            } catch (ClinicalRecordAccessDeniedException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            } catch (ResultAlreadyRecordedException e) {
                problem = "Ese chequeo ya tiene resultado: se cargó recién.";
            } catch (UnsupportedFileException e) {
                problem = AttachmentViewController.UNSUPPORTED;
            } catch (FileTooLargeException e) {
                problem = AttachmentViewController.TOO_LARGE;
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
