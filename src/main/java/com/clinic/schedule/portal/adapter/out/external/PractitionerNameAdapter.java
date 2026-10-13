package com.clinic.schedule.portal.adapter.out.external;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.application.port.PractitionerNamePort;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/** Answers {@link PractitionerNamePort} by asking the practitioner module through its api (D-05). */
@Component
class PractitionerNameAdapter implements PractitionerNamePort {

    private final PractitionerReader practitioners;

    PractitionerNameAdapter(PractitionerReader practitioners) {
        this.practitioners = practitioners;
    }

    @Override
    public String nameOf(UUID practitionerId) {
        return practitioners.findAll().stream()
                .filter(practitioner -> practitioner.id().equals(practitionerId))
                .map(PractitionerSummary::fullName)
                .findFirst()
                .orElse("");
    }
}
