package com.clinic.schedule.clinicalrecord.domain.exception;

/** The file is not a PDF, a PNG or a JPEG, whatever its name says (D-57). A 415. */
public class UnsupportedFileException extends RuntimeException {

    public UnsupportedFileException() {
        super("Only PDF, PNG and JPEG files are attached to a clinical record");
    }
}
