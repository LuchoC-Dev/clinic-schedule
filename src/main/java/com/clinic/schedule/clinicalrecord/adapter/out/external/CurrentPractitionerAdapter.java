package com.clinic.schedule.clinicalrecord.adapter.out.external;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.clinic.schedule.clinicalrecord.application.port.CurrentPractitionerPort;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.security.api.CurrentUserReader;

/** Answers {@link CurrentPractitionerPort} by asking the security module through its api (D-05). */
@Component
class CurrentPractitionerAdapter implements CurrentPractitionerPort {

    private final CurrentUserReader currentUser;

    CurrentPractitionerAdapter(CurrentUserReader currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public Optional<PractitionerId> practitionerId() {
        return currentUser.practitionerId().map(PractitionerId::new);
    }
}
