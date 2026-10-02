package com.clinic.schedule.reminder.adapter.out.external;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.reminder.application.port.PractitionerNamesPort;

/** Answers {@link PractitionerNamesPort} by asking the practitioner module through its api (D-05). */
@Component
class PractitionerNamesAdapter implements PractitionerNamesPort {

    private final PractitionerReader practitioners;

    PractitionerNamesAdapter(PractitionerReader practitioners) {
        this.practitioners = practitioners;
    }

    @Override
    public Map<UUID, String> names() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }
}
