package com.clinic.schedule.appointment.adapter.out.external;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.application.port.AgendaAccessPort;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.security.api.CurrentUserReader;

/** Answers {@link AgendaAccessPort} by asking the security module through its api (D-05). */
@Component
class AgendaAccessAdapter implements AgendaAccessPort {

    private final CurrentUserReader currentUser;

    AgendaAccessAdapter(CurrentUserReader currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public Optional<PractitionerId> restrictedTo() {
        return currentUser.practitionerId().map(PractitionerId::new);
    }
}
