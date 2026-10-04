package com.clinic.schedule.portal.adapter.in.external;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.api.PatientLoginWriter;
import com.clinic.schedule.portal.application.usecase.RecordLoginUseCase;

/** Answers this module's {@link PatientLoginWriter} for the security module, through its use case (D-86). */
@Component
class PatientLoginWriterAdapter implements PatientLoginWriter {

    private final RecordLoginUseCase logins;

    PatientLoginWriterAdapter(RecordLoginUseCase logins) {
        this.logins = logins;
    }

    @Override
    public void loginFailed(UUID accountId) {
        logins.failed(accountId);
    }

    @Override
    public void loginSucceeded(UUID accountId) {
        logins.succeeded(accountId);
    }
}
