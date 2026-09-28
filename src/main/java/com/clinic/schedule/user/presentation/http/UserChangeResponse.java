package com.clinic.schedule.user.presentation.http;

import java.time.LocalDateTime;

import com.clinic.schedule.user.entity.AppUserChange;
import com.clinic.schedule.user.entity.UserAction;

/** What an admin did to a user, by whom and when (D-68). */
record UserChangeResponse(UserAction action, String changedBy, LocalDateTime changedAt) {

    static UserChangeResponse of(AppUserChange change) {
        return new UserChangeResponse(change.getAction(), change.getChangedBy(), change.getChangedAt());
    }
}
