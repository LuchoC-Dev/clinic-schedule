package com.clinic.schedule.portal.adapter.out.external;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.application.port.AccountNamePort;
import com.clinic.schedule.security.api.PatientUsername;

/** Answers {@link AccountNamePort} with the security module's name for a patient's account (D-81). */
@Component
class AccountNameAdapter implements AccountNamePort {

    @Override
    public String nameOf(UUID accountId) {
        return PatientUsername.of(accountId);
    }
}
