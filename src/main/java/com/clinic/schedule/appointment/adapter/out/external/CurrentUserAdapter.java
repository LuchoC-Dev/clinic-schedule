package com.clinic.schedule.appointment.adapter.out.external;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.application.port.CurrentUserPort;
import com.clinic.schedule.security.api.CurrentUserReader;

/** Answers {@link CurrentUserPort} by asking the security module through its api (D-05). */
@Component
class CurrentUserAdapter implements CurrentUserPort {

    private final CurrentUserReader currentUser;

    CurrentUserAdapter(CurrentUserReader currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public String username() {
        return currentUser.username();
    }
}
