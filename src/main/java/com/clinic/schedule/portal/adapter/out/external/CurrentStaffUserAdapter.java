package com.clinic.schedule.portal.adapter.out.external;

import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.application.port.CurrentUserPort;
import com.clinic.schedule.security.api.CurrentUserReader;

/** Answers {@link CurrentUserPort}, who at reception is acting, by asking the security module's api (D-05). */
@Component
class CurrentStaffUserAdapter implements CurrentUserPort {

    private final CurrentUserReader currentUser;

    CurrentStaffUserAdapter(CurrentUserReader currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public String username() {
        return currentUser.username();
    }
}
