package com.clinic.schedule.clinicalrecord.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.clinic.schedule.clinicalrecord.domain.exception.FileTooLargeException;
import com.clinic.schedule.clinicalrecord.domain.exception.UnsupportedFileException;

/**
 * A file attached to a consultation or a check: who uploaded it, when, what it is and its
 * fingerprint (D-57). It is added to the record and never changed nor deleted; a file attached by
 * mistake is said so in an addendum.
 *
 * <p>It holds what describes the file, not the file: the content is stored apart, by the
 * {@code AttachmentContentStore} port, under the attachment's id, so where files live can change
 * without the record noticing. The SHA-256 fingerprint lets anyone tell the stored content is the
 * one uploaded.
 */
public class Attachment {

    /** The largest file a record keeps. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** As long as the column that stores it (D-38). */
    public static final int MAX_FILE_NAME_LENGTH = 255;

    private final AttachmentId id;
    private final PatientId patientId;
    private final AttachmentOwner owner;
    private final PractitionerId uploadedBy;
    private final LocalDateTime uploadedAt;
    private final String fileName;
    private final FileKind kind;
    private final long sizeBytes;
    private final String sha256;

    private Attachment(AttachmentId id, PatientId patientId, AttachmentOwner owner, PractitionerId uploadedBy,
            LocalDateTime uploadedAt, String fileName, FileKind kind, long sizeBytes, String sha256) {
        this.id = id;
        this.patientId = patientId;
        this.owner = owner;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.fileName = fileName;
        this.kind = kind;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    /**
     * A file just uploaded. Its kind is told by its content, and its name is kept without any path
     * the browser may have sent.
     *
     * @throws UnsupportedFileException when it is not a PDF, a PNG or a JPEG
     * @throws FileTooLargeException when it is larger than {@link #MAX_BYTES}
     */
    public static Attachment upload(AttachmentId id, PatientId patientId, AttachmentOwner owner,
            PractitionerId uploadedBy, LocalDateTime uploadedAt, String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("An attachment needs a file");
        }
        if (content.length > MAX_BYTES) {
            throw new FileTooLargeException(MAX_BYTES);
        }
        FileKind kind = FileKind.of(content).orElseThrow(UnsupportedFileException::new);
        return restore(id, patientId, owner, uploadedBy, uploadedAt, cleanName(fileName, kind), kind, content.length,
                sha256Of(content));
    }

    /**
     * An attachment read back from storage, checked the same way. Public because the storage
     * adapter lives in another package.
     */
    public static Attachment restore(AttachmentId id, PatientId patientId, AttachmentOwner owner,
            PractitionerId uploadedBy, LocalDateTime uploadedAt, String fileName, FileKind kind, long sizeBytes,
            String sha256) {
        if (id == null || patientId == null || owner == null || uploadedBy == null || uploadedAt == null
                || kind == null) {
            throw new IllegalArgumentException("An attachment needs an id, a patient, an owner, who, when and a kind");
        }
        if (fileName == null || fileName.isBlank() || fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("An attachment needs a file name of at most " + MAX_FILE_NAME_LENGTH
                    + " characters");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_BYTES) {
            throw new IllegalArgumentException("An attachment is between one byte and " + MAX_BYTES + " bytes");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("An attachment needs its SHA-256 fingerprint");
        }
        return new Attachment(id, patientId, owner, uploadedBy, uploadedAt, fileName, kind, sizeBytes, sha256);
    }

    /** The name without a path, control characters or excess length; a name of its kind when none is left. */
    private static String cleanName(String fileName, FileKind kind) {
        String name = fileName == null ? "" : fileName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "adjunto." + kind.name().toLowerCase();
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(name.length() - MAX_FILE_NAME_LENGTH) : name;
    }

    private static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    public AttachmentId id() {
        return id;
    }

    public PatientId patientId() {
        return patientId;
    }

    public AttachmentOwner owner() {
        return owner;
    }

    public PractitionerId uploadedBy() {
        return uploadedBy;
    }

    public LocalDateTime uploadedAt() {
        return uploadedAt;
    }

    public String fileName() {
        return fileName;
    }

    public FileKind kind() {
        return kind;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String sha256() {
        return sha256;
    }
}
