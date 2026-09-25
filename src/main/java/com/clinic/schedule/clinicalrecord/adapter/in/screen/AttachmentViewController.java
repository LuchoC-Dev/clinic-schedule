package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.usecase.AddAttachmentUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.DownloadAttachmentUseCase;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.exception.FileTooLargeException;
import com.clinic.schedule.clinicalrecord.domain.exception.UnsupportedFileException;

/**
 * Attaching a file to a consultation or a check from the clinical record screen, and downloading
 * one (D-57). An upload goes back to the record, saying what happened; a user who may not read the
 * record gets a 403, for the download as well.
 */
@Controller
class AttachmentViewController {

    static final String UNSUPPORTED = "Solo se adjuntan archivos PDF, PNG o JPG.";
    static final String TOO_LARGE = "El archivo supera los 10 MB.";

    private final AddAttachmentUseCase addAttachment;
    private final DownloadAttachmentUseCase download;

    AttachmentViewController(AddAttachmentUseCase addAttachment, DownloadAttachmentUseCase download) {
        this.addAttachment = addAttachment;
        this.download = download;
    }

    @PostMapping("/consultations/{id}/attachments")
    String attachToConsultation(@PathVariable UUID id, @RequestParam UUID patientId,
            @RequestParam(required = false) MultipartFile file, RedirectAttributes flash) throws IOException {
        return attach(new AttachmentOwner.OfConsultation(new ConsultationId(id)), patientId, file, flash);
    }

    @PostMapping("/checks/{id}/attachments")
    String attachToCheck(@PathVariable UUID id, @RequestParam UUID patientId,
            @RequestParam(required = false) MultipartFile file, RedirectAttributes flash) throws IOException {
        return attach(new AttachmentOwner.OfCheck(new CheckId(id)), patientId, file, flash);
    }

    /** The file, with its type as told by its content and its name to save it under. */
    @GetMapping("/attachments/{id}")
    ResponseEntity<byte[]> download(@PathVariable UUID id) {
        DownloadAttachmentUseCase.Download found;
        try {
            found = download.handle(new AttachmentId(id));
        } catch (ClinicalRecordAccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.attachment().kind().mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(found.attachment().fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(found.content());
    }

    private String attach(AttachmentOwner owner, UUID patientId, MultipartFile file, RedirectAttributes flash)
            throws IOException {
        String problem = null;
        if (file == null || file.isEmpty()) {
            problem = "Elegí un archivo para adjuntar.";
        } else {
            try {
                addAttachment.handle(new AddAttachmentUseCase.Command(owner, file.getOriginalFilename(), file.getBytes()));
            } catch (ClinicalRecordAccessDeniedException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            } catch (UnsupportedFileException e) {
                problem = UNSUPPORTED;
            } catch (FileTooLargeException e) {
                problem = TOO_LARGE;
            }
        }
        if (problem != null) {
            flash.addFlashAttribute("error", problem);
        } else {
            flash.addFlashAttribute("message", "Archivo adjuntado.");
        }
        return "redirect:/clinical-records/" + patientId;
    }
}
