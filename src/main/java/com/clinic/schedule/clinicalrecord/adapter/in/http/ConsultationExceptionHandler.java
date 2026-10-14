package com.clinic.schedule.clinicalrecord.adapter.in.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.clinic.schedule.clinicalrecord.application.exception.CheckChangedException;
import com.clinic.schedule.clinicalrecord.application.exception.CheckNotFoundException;
import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.ConsultationChangedException;
import com.clinic.schedule.clinicalrecord.application.exception.UnknownOrderException;
import com.clinic.schedule.clinicalrecord.domain.exception.CheckNotPendingException;
import com.clinic.schedule.clinicalrecord.domain.exception.FileTooLargeException;
import com.clinic.schedule.clinicalrecord.domain.exception.NotTheOrderAuthorException;
import com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException;
import com.clinic.schedule.clinicalrecord.domain.exception.ReasonRequiredException;
import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;
import com.clinic.schedule.clinicalrecord.domain.exception.UnsupportedFileException;
import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyChangedException;
import com.clinic.schedule.clinicalrecord.application.exception.RecordCopyNotFoundException;
import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * Turns this module's refusals into HTTP answers, for its own controller only (D-23). Malformed
 * requests are answered once for every module, in {@code shared/http}.
 */
@RestControllerAdvice(assignableTypes = {ConsultationController.class, CheckController.class, OrderController.class,
        AttachmentController.class, RecordCopyController.class})
class ConsultationExceptionHandler {

    /** Not this user's to read or to write: a 403, never an empty record (D-50). */
    @ExceptionHandler(ClinicalRecordAccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(ClinicalRecordAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", e));
    }

    /** Another addendum was added at the same time: nothing was saved, and sending it again works (D-52). */
    @ExceptionHandler(ConsultationChangedException.class)
    ResponseEntity<ErrorResponse> conflict(ConsultationChangedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e));
    }

    /** A check has one result, and it never changes: a second one is refused (D-53). */
    @ExceptionHandler(ResultAlreadyRecordedException.class)
    ResponseEntity<ErrorResponse> resultAlreadyRecorded(ResultAlreadyRecordedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e));
    }

    /** Access asked to report a check that does not exist (D-54). */
    @ExceptionHandler(CheckNotFoundException.class)
    ResponseEntity<ErrorResponse> checkNotFound(CheckNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e));
    }

    /** Access asked to report a check that already has its result (D-54). */
    @ExceptionHandler(CheckNotPendingException.class)
    ResponseEntity<ErrorResponse> checkNotPending(CheckNotPendingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e));
    }

    /** Access asked without a reason, when the practice requires one (D-54). */
    @ExceptionHandler(ReasonRequiredException.class)
    ResponseEntity<ErrorResponse> reasonRequired(ReasonRequiredException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e));
    }

    /** The order was already fulfilled or cancelled, maybe a moment ago (D-55). */
    @ExceptionHandler(OrderNotOpenException.class)
    ResponseEntity<ErrorResponse> orderNotOpen(OrderNotOpenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e));
    }

    /** Only whoever issued an order may cancel it (D-55). */
    @ExceptionHandler(NotTheOrderAuthorException.class)
    ResponseEntity<ErrorResponse> notTheOrderAuthor(NotTheOrderAuthorException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(403, "Forbidden", e));
    }

    /** A check said to fulfil an order that is not one of its patient's (D-55). */
    @ExceptionHandler(UnknownOrderException.class)
    ResponseEntity<ErrorResponse> unknownOrder(UnknownOrderException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e));
    }

    /** Not a PDF, a PNG or a JPEG, whatever its name says (D-57). */
    @ExceptionHandler(UnsupportedFileException.class)
    ResponseEntity<ErrorResponse> unsupportedFile(UnsupportedFileException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(415, "Unsupported media type", e));
    }

    /** Larger than a record keeps (D-57). */
    @ExceptionHandler(FileTooLargeException.class)
    ResponseEntity<ErrorResponse> fileTooLarge(FileTooLargeException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(413, "Payload too large", e));
    }

    /** An upload without its file part (D-57). */
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorResponse> missingFile(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad request", "Part '" + e.getRequestPartName() + "' is required"));
    }

    /** Someone else changed the check at the same time: nothing was saved, and sending it again works (D-58). */
    @ExceptionHandler(CheckChangedException.class)
    ResponseEntity<ErrorResponse> checkChanged(CheckChangedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e));
    }

    /** A consultation that breaks its own rules, when Bean Validation did not already catch it. */
    /** A copy that does not exist: the request names nobody's content (D-95). */
    @ExceptionHandler(RecordCopyNotFoundException.class)
    ResponseEntity<ErrorResponse> copyNotFound(RecordCopyNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e));
    }

    /** A copy refusing a step — authenticated already, put together again since, not reviewed —, or changed meanwhile. */
    @ExceptionHandler({RecordCopyRefusedException.class, RecordCopyChangedException.class})
    ResponseEntity<ErrorResponse> copyRefused(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e));
    }
}
