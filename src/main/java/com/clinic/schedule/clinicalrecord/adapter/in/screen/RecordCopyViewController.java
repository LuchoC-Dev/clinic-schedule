package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyChangedException;
import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyNotFoundException;
import com.clinic.schedule.clinicalrecord.application.usecase.AddAttachmentUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.AuthenticateRecordCopyUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordCopiesUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RequestAccessUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ViewRecordCopyUseCase;
import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.AccessGrantPolicy;
import com.clinic.schedule.clinicalrecord.domain.AttachmentOwner;
import com.clinic.schedule.clinicalrecord.domain.CopyDelivery;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;
import com.clinic.schedule.clinicalrecord.domain.exception.FileTooLargeException;
import com.clinic.schedule.clinicalrecord.domain.exception.ReasonRequiredException;
import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException;
import com.clinic.schedule.clinicalrecord.domain.exception.UnsupportedFileException;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The practitioners' screens over the copies of records the patients asked for (D-95): "Copias pedidas", those
 * waiting with the time left and a box to check a code printed on a copy; and each copy, to review it — its PDF,
 * a reading —, upload what was missing to the record (D-94) and put it together again, and authenticate it; or,
 * for a record they may not read, ask for access to do so (D-54). Thin, as every screen (D-02).
 */
@Controller
class RecordCopyViewController {

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ListRecordCopiesUseCase list;
    private final ViewRecordCopyUseCase view;
    private final AuthenticateRecordCopyUseCase authenticate;
    private final AddAttachmentUseCase addAttachment;
    private final RequestAccessUseCase requestAccess;
    private final AccessGrantPolicy grantPolicy;
    private final PatientReader patients;
    private final PractitionerReader practitioners;
    private final Clock clock;

    RecordCopyViewController(ListRecordCopiesUseCase list, ViewRecordCopyUseCase view,
            AuthenticateRecordCopyUseCase authenticate, AddAttachmentUseCase addAttachment,
            RequestAccessUseCase requestAccess, AccessGrantPolicy grantPolicy, PatientReader patients,
            PractitionerReader practitioners, Clock clock) {
        this.list = list;
        this.view = view;
        this.authenticate = authenticate;
        this.addAttachment = addAttachment;
        this.requestAccess = requestAccess;
        this.grantPolicy = grantPolicy;
        this.patients = patients;
        this.practitioners = practitioners;
        this.clock = clock;
    }

    @GetMapping("/record-copies")
    String waiting(Model model) {
        List<ListRecordCopiesUseCase.Waiting> found = forbiddenUnlessPractitioner(list::waiting);
        Map<UUID, PatientSummary> people = people(found.stream().map(ListRecordCopiesUseCase.Waiting::copy).toList());
        LocalDateTime now = LocalDateTime.now(clock);
        model.addAttribute("rows", found.stream()
                .map(waiting -> CopyRow.of(waiting.copy(), waiting.mayRead(), now, people, Map.of()))
                .toList());
        model.addAttribute("readOnly", true);
        return "record-copies";
    }

    /** The copy a code printed on paper belongs to; an unknown code goes back to the list, saying so. */
    @GetMapping("/record-copies/verify")
    String verify(@RequestParam(required = false) String code, RedirectAttributes flash) {
        try {
            return "redirect:/record-copies/" + forbiddenUnlessPractitioner(() -> view.findByCode(code)).copy().id().value();
        } catch (RecordCopyNotFoundException e) {
            flash.addFlashAttribute("error", "No hay ninguna copia con ese código.");
            return "redirect:/record-copies";
        }
    }

    @GetMapping("/record-copies/{id}")
    String one(@PathVariable UUID id, Model model) {
        ViewRecordCopyUseCase.View found;
        try {
            found = forbiddenUnlessPractitioner(() -> view.find(new RecordCopyId(id)));
        } catch (RecordCopyNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        RecordCopy copy = found.copy();
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        model.addAttribute("copy", CopyRow.of(copy, found.mayRead(), LocalDateTime.now(clock), people(List.of(copy)),
                names));
        model.addAttribute("assembly", copy.assembly());
        model.addAttribute("authenticated", copy.isAuthenticated());
        model.addAttribute("onPaper", copy.delivery() == CopyDelivery.PAPER);
        model.addAttribute("reasonRequired", grantPolicy.isReasonRequired());
        model.addAttribute("readOnly", true);
        return "record-copy";
    }

    /** The copy to review or print, shown in the browser: a reading of the record. */
    @GetMapping("/record-copies/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        ViewRecordCopyUseCase.Document document;
        try {
            document = forbiddenUnlessPractitioner(() -> view.document(new RecordCopyId(id)));
        } catch (RecordCopyNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("copia-historia-clinica.pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.content());
    }

    /** Uploads what was missing to the record as a whole (D-94), and comes back to the copy to put it together again. */
    @PostMapping("/record-copies/{id}/attachments")
    String upload(@PathVariable UUID id, @RequestParam(required = false) MultipartFile file, RedirectAttributes flash)
            throws IOException {
        RecordCopy copy = forbiddenUnlessPractitioner(() -> view.find(new RecordCopyId(id))).copy();
        if (file == null || file.isEmpty()) {
            flash.addFlashAttribute("error", "Elegí un archivo para subir.");
        } else {
            try {
                forbiddenUnlessPractitioner(() -> addAttachment.handle(new AddAttachmentUseCase.Command(
                        new AttachmentOwner.OfRecord(copy.patientId()), file.getOriginalFilename(), bytesOf(file))));
                flash.addFlashAttribute("message",
                        "Archivo subido a la historia. Volvé a armar la copia para que lo incluya.");
            } catch (UnsupportedFileException e) {
                flash.addFlashAttribute("error", AttachmentViewController.UNSUPPORTED);
            } catch (FileTooLargeException e) {
                flash.addFlashAttribute("error", AttachmentViewController.TOO_LARGE);
            }
        }
        return "redirect:/record-copies/" + id;
    }

    @PostMapping("/record-copies/{id}/reassembly")
    String reassemble(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            forbiddenUnlessPractitioner(() -> authenticate.reassemble(new RecordCopyId(id)));
            flash.addFlashAttribute("message", "Copia armada de nuevo con la historia de ahora. Revisala antes de autenticarla.");
        } catch (RecordCopyRefusedException | RecordCopyChangedException e) {
            flash.addFlashAttribute("error", "La copia ya está autenticada: no se arma de nuevo.");
        }
        return "redirect:/record-copies/" + id;
    }

    @PostMapping("/record-copies/{id}/authentication")
    String authenticate(@PathVariable UUID id, @RequestParam int assembly,
            @RequestParam(required = false) boolean reviewed, RedirectAttributes flash) {
        try {
            RecordCopy copy = forbiddenUnlessPractitioner(
                    () -> authenticate.authenticate(new RecordCopyId(id), assembly, reviewed));
            flash.addFlashAttribute("message", copy.delivery() == CopyDelivery.PAPER
                    ? "Copia autenticada. Imprimila, firmala, sellala y dejala en recepción en un sobre cerrado."
                    : "Copia autenticada. El paciente ya puede descargarla desde su cuenta.");
        } catch (RecordCopyRefusedException e) {
            flash.addFlashAttribute("error", switch (e.reason()) {
                case NOT_REVIEWED -> "Marcá que revisaste la copia antes de autenticarla.";
                case REASSEMBLED_SINCE -> "La copia se armó de nuevo después de que la abriste: revisala otra vez.";
                default -> "La copia ya está autenticada.";
            });
        } catch (RecordCopyChangedException e) {
            flash.addFlashAttribute("error", "La copia ya está autenticada.");
        }
        return "redirect:/record-copies/" + id;
    }

    /** Asks for access to the record to authenticate its copy; granted, it leads back to the copy. */
    @PostMapping("/record-copies/{id}/access-grants")
    String requestAccess(@PathVariable UUID id, @RequestParam(required = false) String reason,
            RedirectAttributes flash) {
        try {
            AccessGrant grant = forbiddenUnlessPractitioner(() -> requestAccess.forCopy(
                    new RequestAccessUseCase.CopyCommand(new RecordCopyId(id), reason)));
            flash.addFlashAttribute("message", "Acceso autorizado hasta el "
                    + grant.validUntil().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        } catch (ReasonRequiredException e) {
            flash.addFlashAttribute("error", "Escribí el motivo del pedido de acceso.");
        } catch (RecordCopyRefusedException e) {
            flash.addFlashAttribute("error", "La copia ya está autenticada: no hace falta acceso para eso.");
        } catch (RecordCopyNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", "El motivo tiene hasta 500 caracteres.");
        }
        return "redirect:/record-copies/" + id;
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("The uploaded file could not be read", e);
        }
    }

    private static <T> T forbiddenUnlessPractitioner(Supplier<T> action) {
        try {
            return action.get();
        } catch (ClinicalRecordAccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    private Map<UUID, PatientSummary> people(List<RecordCopy> copies) {
        return patients.findAll(copies.stream()
                        .flatMap(copy -> Stream.of(copy.patientId().value(), copy.requestedBy()))
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
    }

    /** "Quedan 31 h", "Quedan 40 min", "Vencida hace 3 h": how long is left of the 48 hours, or how late it is. */
    static String timeLeftLabel(Duration left) {
        Duration amount = left.abs();
        String said = amount.toHours() >= 1 ? amount.toHours() + " h" : Math.max(1, amount.toMinutes()) + " min";
        return left.isNegative() ? "Vencida hace " + said : "Quedan " + said;
    }

    /** One copy as the screens show it, in Spanish (D-13). */
    record CopyRow(UUID id, UUID patientId, String patient, String requestedBy, String requestedAt, String delivery,
            String code, String assembledAt, String timeLeft, boolean late, String authenticated, String handedOver,
            boolean mayRead) {

        static CopyRow of(RecordCopy copy, boolean mayRead, LocalDateTime now, Map<UUID, PatientSummary> people,
                Map<UUID, String> names) {
            PatientSummary patient = people.get(copy.patientId().value());
            PatientSummary asker = people.get(copy.requestedBy());
            return new CopyRow(copy.id().value(), copy.patientId().value(),
                    labelOf(patient),
                    copy.requestedBy().equals(copy.patientId().value()) ? "El paciente"
                            : (asker == null ? "(desconocido)" : asker.fullName()),
                    copy.requestedAt().format(DAY_TIME),
                    copy.delivery() == CopyDelivery.PDF ? "PDF en su cuenta" : "Papel, en recepción",
                    copy.code().formatted(),
                    copy.assembledAt().format(DAY_TIME),
                    timeLeftLabel(copy.timeLeft(now)),
                    copy.isLate(now),
                    copy.authentication()
                            .map(authentication -> ClinicalRecordEntry.nameOf(names,
                                    authentication.practitionerId().value()) + ", " + authentication.at().format(DAY_TIME))
                            .orElse(null),
                    copy.handover().map(handover -> handover.at().format(DAY_TIME)).orElse(null),
                    mayRead);
        }

        static String labelOf(PatientSummary patient) {
            return patient == null ? "(paciente desconocido)"
                    : patient.document() == null ? patient.fullName()
                    : patient.fullName() + " — " + patient.document();
        }
    }
}
