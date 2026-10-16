package com.clinic.schedule.clinicalrecord.adapter.in.http;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.clinicalrecord.domain.AccessGrantPolicy;

/**
 * How access to a record is asked for, for practitioners only (D-54, D-105): whether the practice requires a reason,
 * a setting the law will decide, so the screens say "Motivo" or "Motivo (opcional)". Asking
 * itself is a check's or a copy's, on their own routes.
 */
@RestController
class AccessGrantController {

    private final AccessGrantPolicy policy;

    AccessGrantController(AccessGrantPolicy policy) {
        this.policy = policy;
    }

    @GetMapping("/api/access-grants/settings")
    Settings settings() {
        return new Settings(policy.isReasonRequired());
    }

    /** @param reasonRequired whether asking for access without a reason is refused */
    record Settings(boolean reasonRequired) {
    }
}
