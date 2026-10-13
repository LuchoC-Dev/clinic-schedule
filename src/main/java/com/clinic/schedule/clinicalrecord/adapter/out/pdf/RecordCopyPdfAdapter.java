package com.clinic.schedule.clinicalrecord.adapter.out.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.clinic.schedule.clinicalrecord.application.port.RecordCopyDocumentPort;
import com.clinic.schedule.clinicalrecord.domain.Addendum;
import com.clinic.schedule.clinicalrecord.domain.Attachment;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.FileKind;
import com.clinic.schedule.clinicalrecord.domain.ItemKind;
import com.clinic.schedule.clinicalrecord.domain.OrderOutcome;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyContent;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyContent.CopiedCheck;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyContent.CopiedConsultation;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyContent.CopiedOrder;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * Answers {@link RecordCopyDocumentPort} with a PDF, through OpenPDF (D-95): the practice, the patient, when the
 * record is copied as it stood, the code and who authenticated it; then every entry, the oldest first, each with
 * its author and when it was written; then each file attached, as an annex — the pages of a PDF, a picture
 * scaled to the page. Every page carries the code and its number. The words are Spanish, as everything the
 * patients read (D-13).
 *
 * <p>The names come from the patient and practitioner modules' api, read here: the use cases need none (D-33). A
 * file that cannot be put in the copy — a PDF that cannot be read — leaves a page saying so, with its fingerprint,
 * for the practitioner who reviews the copy to see it.
 */
@Component
class RecordCopyPdfAdapter implements RecordCopyDocumentPort {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, Color.BLACK);
    private static final Font HEADING = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
    private static final Font LABEL = new Font(Font.HELVETICA, 9, Font.BOLD, Color.DARK_GRAY);
    private static final Font TEXT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);
    private static final Font WARNING = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(0xB0, 0x00, 0x20));

    private final PatientReader patients;
    private final PractitionerReader practitioners;
    private final String practice;
    private final String address;
    private final String phone;

    RecordCopyPdfAdapter(PatientReader patients, PractitionerReader practitioners,
            @Value("${app.practice.name}") String practice, @Value("${app.practice.address}") String address,
            @Value("${app.practice.phone}") String phone) {
        this.patients = patients;
        this.practitioners = practitioners;
        this.practice = practice;
        this.address = address;
        this.phone = phone;
    }

    @Override
    public byte[] render(RecordCopy copy, RecordCopyContent content, Map<AttachmentId, byte[]> files) {
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        Map<UUID, PatientSummary> people = new HashMap<>();
        patients.findAll(List.of(copy.patientId().value(), copy.requestedBy()))
                .forEach(person -> people.put(person.id(), person));
        List<Attachment> annexes = content.attachments();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer(copy.code().formatted()));
            document.addTitle("Copia de historia clínica");
            document.open();
            header(document, copy, people, names);
            entries(document, content, names, annexes);
            for (int i = 0; i < annexes.size(); i++) {
                annex(document, writer, i + 1, annexes.get(i), files.get(annexes.get(i).id()), names);
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("The copy of the record could not be written", e);
        }
        return out.toByteArray();
    }

    private void header(Document document, RecordCopy copy, Map<UUID, PatientSummary> people, Map<UUID, String> names)
            throws DocumentException {
        document.add(new Paragraph(practice, HEADING));
        document.add(new Paragraph(address + " · " + phone, SMALL));
        Paragraph title = new Paragraph("Copia de historia clínica", TITLE);
        title.setSpacingBefore(14);
        title.setSpacingAfter(8);
        document.add(title);

        PatientSummary patient = people.get(copy.patientId().value());
        line(document, "Paciente", patient == null ? "(desconocido)"
                : patient.fullName() + (patient.document() == null ? "" : " — " + patient.document()));
        if (patient != null && patient.birthDate() != null) {
            line(document, "Fecha de nacimiento", patient.birthDate().format(DAY));
        }
        PatientSummary asker = people.get(copy.requestedBy());
        line(document, "Pedida", copy.requestedAt().format(WHEN)
                + (copy.requestedBy().equals(copy.patientId().value()) || asker == null ? " por el paciente"
                        : " por " + asker.fullName()));
        line(document, "Historia al", copy.assembledAt().format(WHEN)
                + " — todo lo registrado hasta ese momento, de todos los profesionales");
        line(document, "Código de verificación", copy.code().formatted());
        copy.authentication().ifPresentOrElse(
                authentication -> line(document, "Autenticada", "por " + nameOf(names, authentication.practitionerId().value())
                        + " (usuario " + authentication.username() + ") el " + authentication.at().format(WHEN)
                        + ", con su ingreso a la aplicación como firma electrónica (Ley 25.506, art. 5;"
                        + " Decreto 1089/2012, art. 13)."),
                () -> {
                    Paragraph draft = new Paragraph("SIN AUTENTICAR — copia para revisar, no para entregar.", WARNING);
                    draft.setSpacingBefore(6);
                    add(document, draft);
                });
        Paragraph law = new Paragraph("Copia entregada al paciente según la Ley 26.529, art. 14. El código de"
                + " verificación permite cotejarla con la historia en el consultorio.", SMALL);
        law.setSpacingBefore(6);
        law.setSpacingAfter(10);
        document.add(law);
    }

    private void entries(Document document, RecordCopyContent content, Map<UUID, String> names,
            List<Attachment> annexes) throws DocumentException {
        if (content.isEmpty()) {
            document.add(new Paragraph("La historia no tiene nada registrado.", TEXT));
            return;
        }
        List<Timed> timed = new ArrayList<>();
        content.consultations().forEach(consultation ->
                timed.add(new Timed(consultation.consultation().recordedAt(), consultation, null)));
        content.checksWithoutConsultation().forEach(check ->
                timed.add(new Timed(check.check().performedAt(), null, check)));
        timed.sort(Comparator.comparing(Timed::at));
        for (Timed entry : timed) {
            if (entry.consultation() != null) {
                consultation(document, entry.consultation(), names, annexes);
            } else {
                check(document, entry.check(), names, annexes, 0);
            }
        }
        if (!content.recordFiles().isEmpty()) {
            Paragraph heading = new Paragraph("Archivos de la historia", HEADING);
            heading.setSpacingBefore(12);
            document.add(heading);
            for (Attachment file : content.recordFiles()) {
                file(document, file, names, annexes, 0);
            }
        }
    }

    private record Timed(LocalDateTime at, CopiedConsultation consultation, CopiedCheck check) {
    }

    private void consultation(Document document, CopiedConsultation copied, Map<UUID, String> names,
            List<Attachment> annexes) throws DocumentException {
        Paragraph heading = new Paragraph("Consulta — " + copied.consultation().recordedAt().format(WHEN) + " — "
                + nameOf(names, copied.consultation().practitionerId().value()), HEADING);
        heading.setSpacingBefore(12);
        document.add(heading);
        document.add(new Paragraph(copied.consultation().notes(), TEXT));
        for (var item : copied.consultation().items()) {
            indented(document, labelOf(item.kind()) + ": " + item.description(), TEXT, 12);
        }
        for (CopiedCheck check : copied.checks()) {
            check(document, check, names, annexes, 12);
        }
        for (CopiedOrder order : copied.orders()) {
            indented(document, "Orden: " + order.order().description() + " — " + stateOf(order), TEXT, 12);
        }
        for (Attachment file : copied.attachments()) {
            file(document, file, names, annexes, 12);
        }
        for (Addendum addendum : copied.addenda()) {
            addendum(document, addendum, names, 12);
        }
    }

    private void check(Document document, CopiedCheck copied, Map<UUID, String> names, List<Attachment> annexes,
            float indent) throws DocumentException {
        Paragraph heading = new Paragraph("Chequeo — " + copied.check().performedAt().format(WHEN) + " — "
                + nameOf(names, copied.check().performedBy().value()), indent == 0 ? HEADING : LABEL);
        heading.setIndentationLeft(indent);
        heading.setSpacingBefore(indent == 0 ? 12 : 4);
        document.add(heading);
        indented(document, copied.check().description(), TEXT, indent);
        copied.result().ifPresentOrElse(
                result -> {
                    indented(document, "Resultado — " + result.writtenAt().format(WHEN) + " — "
                            + nameOf(names, result.author().value()), LABEL, indent + 12);
                    indented(document, result.hasText() ? result.text() : "En el archivo adjunto.", TEXT, indent + 12);
                },
                () -> indented(document, "Resultado pendiente.", TEXT, indent + 12));
        for (Attachment file : copied.attachments()) {
            file(document, file, names, annexes, indent + 12);
        }
        for (Addendum addendum : copied.addenda()) {
            addendum(document, addendum, names, indent + 12);
        }
    }

    private void addendum(Document document, Addendum addendum, Map<UUID, String> names, float indent) {
        indented(document, "Aclaración — " + addendum.writtenAt().format(WHEN) + " — "
                + nameOf(names, addendum.author().value()), LABEL, indent);
        indented(document, addendum.text(), TEXT, indent);
    }

    private void file(Document document, Attachment file, Map<UUID, String> names, List<Attachment> annexes,
            float indent) {
        indented(document, "Archivo adjunto: " + file.fileName() + " — subido el " + file.uploadedAt().format(WHEN)
                + " por " + nameOf(names, file.uploadedBy().value()) + " — anexo " + (annexes.indexOf(file) + 1),
                TEXT, indent);
    }

    private void annex(Document document, PdfWriter writer, int number, Attachment file, byte[] bytes,
            Map<UUID, String> names) throws DocumentException {
        document.newPage();
        document.add(new Paragraph("Anexo " + number + " — " + file.fileName(), HEADING));
        document.add(new Paragraph("Subido el " + file.uploadedAt().format(WHEN) + " por "
                + nameOf(names, file.uploadedBy().value()) + " · " + sizeOf(file.sizeBytes())
                + " · SHA-256 " + file.sha256(), SMALL));
        try {
            if (file.kind() == FileKind.PDF) {
                PdfReader reader = new PdfReader(bytes);
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    if (page > 1) {
                        document.newPage();
                    }
                    fitted(document, Image.getInstance(writer.getImportedPage(reader, page)));
                }
            } else {
                fitted(document, Image.getInstance(bytes));
            }
        } catch (IOException | RuntimeException e) {
            Paragraph missing = new Paragraph("Este archivo no pudo reproducirse en la copia. Se conserva en la"
                    + " historia con la huella SHA-256 de arriba.", WARNING);
            missing.setSpacingBefore(12);
            document.add(missing);
        }
    }

    /** Scaled to what is left of the page, never larger than it was. */
    private static void fitted(Document document, Image image) throws DocumentException {
        float width = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
        float height = document.getPageSize().getHeight() - document.topMargin() - document.bottomMargin() - 40;
        if (image.getScaledWidth() > width || image.getScaledHeight() > height) {
            image.scaleToFit(width, height);
        }
        image.setAlignment(Element.ALIGN_CENTER);
        document.add(image);
    }

    private static void line(Document document, String label, String value) {
        Paragraph line = new Paragraph();
        line.add(new Chunk(label + ": ", LABEL));
        line.add(new Chunk(value, TEXT));
        add(document, line);
    }

    private static void indented(Document document, String text, Font font, float indent) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setIndentationLeft(indent);
        add(document, paragraph);
    }

    private static void add(Document document, Paragraph paragraph) {
        try {
            document.add(paragraph);
        } catch (DocumentException e) {
            throw new IllegalStateException("The copy of the record could not be written", e);
        }
    }

    private static String stateOf(CopiedOrder order) {
        return switch (order.status()) {
            case OPEN -> "abierta";
            case FULFILLED -> "cumplida";
            case CANCELLED -> order.outcome()
                    .map(outcome -> (OrderOutcome.Cancelled) outcome)
                    .map(cancelled -> "anulada el " + cancelled.at().format(WHEN) + ": " + cancelled.reason())
                    .orElse("anulada");
        };
    }

    private static String labelOf(ItemKind kind) {
        return switch (kind) {
            case PRESCRIPTION -> "Receta";
        };
    }

    private static String nameOf(Map<UUID, String> names, UUID practitionerId) {
        return names.getOrDefault(practitionerId, "(profesional desconocido)");
    }

    private static String sizeOf(long bytes) {
        return bytes < 1024 * 1024
                ? Math.max(1, bytes / 1024) + " KB"
                : String.format(Locale.forLanguageTag("es-AR"), "%.1f MB", bytes / (1024.0 * 1024));
    }

    /** The code and "página N de M" at the foot of every page; the total is written once the document closes. */
    private static final class Footer extends PdfPageEventHelper {

        private final String code;
        private BaseFont font;
        private PdfTemplate total;

        Footer(String code) {
            this.code = code;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (IOException | DocumentException e) {
                throw new IllegalStateException("Helvetica is built into every PDF reader", e);
            }
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            String text = "Copia de historia clínica · código " + code + " · página " + writer.getPageNumber() + " de ";
            float x = document.left();
            float y = document.bottom() - 30;
            canvas.beginText();
            canvas.setFontAndSize(font, 8);
            canvas.setTextMatrix(x, y);
            canvas.showText(text);
            canvas.endText();
            canvas.addTemplate(total, x + font.getWidthPoint(text, 8), y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(font, 8);
            total.setTextMatrix(0, 0);
            // The page number has moved on past the last page by the time the document closes.
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
