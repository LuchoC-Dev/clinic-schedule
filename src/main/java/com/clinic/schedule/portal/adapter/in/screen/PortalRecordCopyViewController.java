package com.clinic.schedule.portal.adapter.in.screen;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.portal.application.exception.NotYourRecordException;
import com.clinic.schedule.portal.application.exception.RecordCopyNotReadyException;
import com.clinic.schedule.portal.application.exception.RecordCopyWaitingException;
import com.clinic.schedule.portal.application.usecase.OwnRecordCopiesUseCase;
import com.clinic.schedule.portal.domain.RecordCopyRequest;

/**
 * "Mi historia clínica" (D-95): asking for a copy of one's record, or of a child's under 13, choosing how to get
 * it; following each request; and downloading the PDF once a practitioner authenticated it. Nothing of the record
 * is read on the web: it comes as the copy (D-51).
 */
@Controller
class PortalRecordCopyViewController {

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OwnRecordCopiesUseCase copies;

    PortalRecordCopyViewController(OwnRecordCopiesUseCase copies) {
        this.copies = copies;
    }

    @GetMapping("/portal/record-copies")
    String list(Model model) {
        model.addAttribute("whom", copies.askableFor());
        model.addAttribute("rows", copies.list().stream().map(Row::of).toList());
        return "portal-record-copies";
    }

    @PostMapping("/portal/record-copies")
    String request(@RequestParam(required = false) UUID patient,
            @RequestParam(required = false) RecordCopyRequest.Delivery delivery, RedirectAttributes flash) {
        if (delivery == null) {
            flash.addFlashAttribute("error", "Elegí cómo querés recibir la copia.");
            return "redirect:/portal/record-copies";
        }
        try {
            copies.request(patient, delivery);
            flash.addFlashAttribute("message", "Pedimos la copia. Un profesional la revisa y la autentica dentro de las"
                    + " 48 horas.");
        } catch (NotYourRecordException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (RecordCopyWaitingException e) {
            flash.addFlashAttribute("error", "Ya hay una copia pedida de esa historia: esperá a que esté lista.");
        }
        return "redirect:/portal/record-copies";
    }

    @GetMapping("/portal/record-copies/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] content;
        try {
            content = copies.download(id);
        } catch (NotYourRecordException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (RecordCopyNotReadyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("copia-historia-clinica.pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }

    /** One request as the patient reads it, in Spanish (D-13). */
    record Row(UUID id, String patient, String requestedAt, String delivery, String state, boolean downloadable) {

        static Row of(OwnRecordCopiesUseCase.Entry entry) {
            RecordCopyRequest copy = entry.copy();
            return new Row(copy.id(),
                    entry.own() ? "Tu historia" : "Historia de " + entry.patient().fullName(),
                    copy.requestedAt().format(DAY_TIME),
                    copy.delivery() == RecordCopyRequest.Delivery.PDF ? "PDF" : "Papel",
                    switch (copy.state()) {
                        case WAITING -> "Esperando que un profesional la autentique (a más tardar el "
                                + copy.deadline().format(DAY_TIME) + ")";
                        case READY_TO_DOWNLOAD -> "Lista para descargar";
                        case READY_TO_COLLECT -> "Lista para retirar en recepción, con tu documento";
                        case HANDED_OVER -> "Entregada el " + copy.handedOverAt().format(DAY_TIME);
                    },
                    copy.state() == RecordCopyRequest.State.READY_TO_DOWNLOAD);
        }
    }
}
