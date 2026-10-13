package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.clinicalrecord.domain.exception.FileTooLargeException;
import com.clinic.schedule.clinicalrecord.domain.exception.UnsupportedFileException;

class AttachmentTest {

    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final PractitionerId ARIAS = new PractitionerId(UUID.randomUUID());
    private static final AttachmentOwner OF_A_CHECK = new AttachmentOwner.OfCheck(CheckId.newId());
    private static final LocalDateTime AT = LocalDateTime.of(2026, 3, 10, 10, 15);

    static final byte[] PDF = "%PDF-1.4\n% un informe\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    private static Attachment upload(String name, byte[] content) {
        return Attachment.upload(AttachmentId.newId(), PATIENT, OF_A_CHECK, ARIAS, AT, name, content);
    }

    @Test
    void theKindIsToldByTheContentNotByTheName() {
        assertThat(upload("informe.pdf", PDF).kind()).isEqualTo(FileKind.PDF);
        assertThat(upload("traza.png", PNG).kind()).isEqualTo(FileKind.PNG);
        assertThat(upload("foto.jpg", JPEG).kind()).isEqualTo(FileKind.JPEG);
        assertThat(upload("engañoso.pdf", PNG).kind()).isEqualTo(FileKind.PNG);
        assertThatThrownBy(() -> upload("virus.pdf", "MZ... no es un PDF".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnsupportedFileException.class);
    }

    @Test
    void aFileIsAtMostTenMegabytesAndNotEmpty() {
        byte[] justTen = Arrays.copyOf(PDF, (int) Attachment.MAX_BYTES);
        byte[] overTen = Arrays.copyOf(PDF, (int) Attachment.MAX_BYTES + 1);

        assertThat(upload("grande.pdf", justTen).sizeBytes()).isEqualTo(Attachment.MAX_BYTES);
        assertThatThrownBy(() -> upload("enorme.pdf", overTen)).isInstanceOf(FileTooLargeException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> upload("vacio.pdf", new byte[0]));
    }

    @Test
    void theNameKeepsNoPathAndNeverEndsEmpty() {
        assertThat(upload("C:\\Users\\Arias\\Escritorio\\ecg.pdf", PDF).fileName()).isEqualTo("ecg.pdf");
        assertThat(upload("../../etc/informe.pdf", PDF).fileName()).isEqualTo("informe.pdf");
        assertThat(upload("  ", PDF).fileName()).isEqualTo("adjunto.pdf");
        assertThat(upload(null, JPEG).fileName()).isEqualTo("adjunto.jpeg");
        assertThat(upload("a".repeat(300) + ".pdf", PDF).fileName()).hasSize(255).endsWith(".pdf");
    }

    @Test
    void theFingerprintIsTheContentsSha256() {
        assertThat(upload("informe.pdf", PDF).sha256())
                .hasSize(64)
                .isEqualTo(upload("otro-nombre.pdf", PDF).sha256())
                .isNotEqualTo(upload("traza.png", PNG).sha256());
    }

    @Test
    void anAttachmentBelongsToSomething() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> Attachment.upload(AttachmentId.newId(), PATIENT, null, ARIAS, AT, "informe.pdf", PDF));
        assertThatIllegalArgumentException().isThrownBy(() -> new AttachmentOwner.OfCheck(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new AttachmentOwner.OfConsultation(null));
    }

    @Test
    void aFileOfTheRecordBelongsToThatPatientsRecordAlone() {
        AttachmentOwner ofTheRecord = new AttachmentOwner.OfRecord(PATIENT);

        assertThat(Attachment.upload(AttachmentId.newId(), PATIENT, ofTheRecord, ARIAS, AT, "historia-papel.pdf", PDF)
                .owner()).isEqualTo(ofTheRecord);
        assertThatIllegalArgumentException().isThrownBy(() -> Attachment.upload(AttachmentId.newId(),
                new PatientId(UUID.randomUUID()), ofTheRecord, ARIAS, AT, "historia-papel.pdf", PDF));
        assertThatIllegalArgumentException().isThrownBy(() -> new AttachmentOwner.OfRecord(null));
    }
}
