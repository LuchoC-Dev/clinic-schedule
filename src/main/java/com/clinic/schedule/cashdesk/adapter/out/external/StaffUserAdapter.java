package com.clinic.schedule.cashdesk.adapter.out.external;

import org.springframework.stereotype.Component;

import com.clinic.schedule.cashdesk.application.port.StaffUserPort;
import com.clinic.schedule.security.api.CurrentUserReader;

/** Answers {@link StaffUserPort} by asking the security module through its api (D-05). */
@Component
class StaffUserAdapter implements StaffUserPort {

    private final CurrentUserReader currentUser;

    StaffUserAdapter(CurrentUserReader currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public String username() {
        return currentUser.username();
    }
}
