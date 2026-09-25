package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.clinic.schedule.clinicalrecord.application.usecase.AddAttachmentUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.DownloadAttachmentUseCase;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * Files attached to a consultation or a check, for practitioners only (D-51, D-57): one file per
 * request, as {@code multipart/form-data} with a part named {@code file}; and the download, only
 * for whoever may read the record, never at a public address.
 */
@RestController
class AttachmentController {

    private final AddAttachmentUseCase addAttachment;
    private final DownloadAttachmentUseCase download;
    private final PractitionerReader practitioners;

    AttachmentController(AddAttachmentUseCase addAttachment, DownloadAttachmentUseCase download,
            PractitionerReader practitioners) {
        this.addAttachment = addAttachment;
        this.download = download;
        this.practitioners = practitioners;
    }

    @PostMapping("/api/consultations/{id}/attachments")
    ResponseEntity<AttachmentResponse> attachToConsultation(@PathVariable UUID id, @RequestPart MultipartFile file)
            throws IOException {
        return attach(new AttachmentOwner.OfConsultation(new ConsultationId(id)), file);
    }

    @PostMapping("/api/checks/{id}/attachments")
    ResponseEntity<AttachmentResponse> attachToCheck(@PathVariable UUID id, @RequestPart MultipartFile file)
            throws IOException {
        return attach(new AttachmentOwner.OfCheck(new CheckId(id)), file);
    }

    /** The file, with its type as told by its content and its name to save it under. */
    @GetMapping("/api/attachments/{id}")
    ResponseEntity<byte[]> download(@PathVariable UUID id) {
        DownloadAttachmentUseCase.Download found = download.handle(new AttachmentId(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.attachment().kind().mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(found.attachment().fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(found.content());
    }

    private ResponseEntity<AttachmentResponse> attach(AttachmentOwner owner, MultipartFile file) throws IOException {
        Attachment attachment = addAttachment.handle(
                new AddAttachmentUseCase.Command(owner, file.getOriginalFilename(), file.getBytes()));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        return ResponseEntity
                .created(URI.create("/api/attachments/" + attachment.id().value()))
                .body(AttachmentResponse.of(attachment, names));
    }
}
