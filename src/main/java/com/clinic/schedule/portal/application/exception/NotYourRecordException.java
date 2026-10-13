package com.clinic.schedule.portal.application.exception;

import java.util.UUID;

/**
 * A copy of a record the patient may not ask for or download: someone else's, a child whose relation is not proved,
 * a child already 13 (D-95). A 403, never answered as if it were not there (D-50).
 */
public class NotYourRecordException extends RuntimeException {

    public NotYourRecordException(UUID id) {
        super("Not a record this patient may ask a copy of: " + id);
    }
}
