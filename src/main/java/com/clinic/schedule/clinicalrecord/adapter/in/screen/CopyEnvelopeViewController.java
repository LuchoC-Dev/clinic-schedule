package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyChangedException;
import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyNotFoundException;
import com.clinic.schedule.clinicalrecord.application.usecase.DeliverRecordCopyUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordCopiesUseCase;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;
import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * Reception's "Copias para retirar" (D-95): the copies on paper, authenticated and left in a sealed envelope, with
 * whose they are and who asked — whoever comes for one shows their document —, and handing one over. Never what
 * they hold, nor who authenticated them: reception does not read the record (D-51).
 */
@Controller
class CopyEnvelopeViewController {

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ListRecordCopiesUseCase list;
    private final DeliverRecordCopyUseCase deliver;
    private final PatientReader patients;

    CopyEnvelopeViewController(ListRecordCopiesUseCase list, DeliverRecordCopyUseCase deliver,
            PatientReader patients) {
        this.list = list;
        this.deliver = deliver;
        this.patients = patients;
    }

    @GetMapping("/record-copy-envelopes")
    String envelopes(Model model) {
        List<RecordCopy> found = list.awaitingHandover();
        Map<UUID, PatientSummary> people = patients.findAll(found.stream()
                        .flatMap(copy -> Stream.of(copy.patientId().value(), copy.requestedBy()))
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        model.addAttribute("rows", found.stream()
                .map(copy -> new EnvelopeRow(copy.id().value(),
                        RecordCopyViewController.CopyRow.labelOf(people.get(copy.patientId().value())),
                        copy.requestedBy().equals(copy.patientId().value()) ? "El paciente"
                                : RecordCopyViewController.CopyRow.labelOf(people.get(copy.requestedBy())),
                        copy.authentication().orElseThrow().at().format(DAY_TIME)))
                .toList());
        return "record-copy-envelopes";
    }

    @PostMapping("/record-copy-envelopes/{id}/handover")
    String handOver(@PathVariable UUID id, @RequestParam(required = false) boolean documentSeen,
            RedirectAttributes flash) {
        try {
            deliver.handOver(new RecordCopyId(id), documentSeen);
            flash.addFlashAttribute("message", "Copia entregada.");
        } catch (RecordCopyRefusedException e) {
            flash.addFlashAttribute("error", e.reason() == RecordCopyRefusedException.Reason.DOCUMENT_NOT_SEEN
                    ? "Marcá que viste el documento de quien la retira."
                    : "Esa copia ya fue entregada.");
        } catch (RecordCopyNotFoundException | RecordCopyChangedException e) {
            flash.addFlashAttribute("error", "Esa copia ya fue entregada.");
        }
        return "redirect:/record-copy-envelopes";
    }

    /** One envelope as the list shows it: whose, who asked for it, when it was authenticated. */
    record EnvelopeRow(UUID id, String patient, String requestedBy, String authenticatedAt) {
    }
}
