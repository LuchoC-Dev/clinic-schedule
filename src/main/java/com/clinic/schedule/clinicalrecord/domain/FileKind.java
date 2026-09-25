package com.clinic.schedule.clinicalrecord.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kinds of file a clinical record accepts, told by the file's first bytes — never by the name
 * or the type the browser declares, which anyone can change (D-57).
 */
public enum FileKind {

    PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
    PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}),
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final String mediaType;
    private final byte[] signature;

    FileKind(String mediaType, byte[] signature) {
        this.mediaType = mediaType;
        this.signature = signature;
    }

    /** The kind whose signature the content starts with, if any. */
    public static Optional<FileKind> of(byte[] content) {
        return Arrays.stream(values()).filter(kind -> kind.startsWith(content)).findFirst();
    }

    /** The kind with that media type, as stored. */
    public static FileKind withMediaType(String mediaType) {
        return Arrays.stream(values())
                .filter(kind -> kind.mediaType.equals(mediaType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not a kind of file a record keeps: " + mediaType));
    }

    private boolean startsWith(byte[] content) {
        return content != null && content.length >= signature.length
                && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }

    public String mediaType() {
        return mediaType;
    }
}
