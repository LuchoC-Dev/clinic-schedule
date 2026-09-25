package com.clinic.schedule.clinicalrecord.domain.exception;

/** The file is larger than a clinical record keeps (D-57). A 413. */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long maxBytes) {
        super("A file attached to a clinical record is at most " + (maxBytes / (1024 * 1024)) + " MB");
    }
}
