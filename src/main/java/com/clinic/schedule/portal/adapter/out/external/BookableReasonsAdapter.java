package com.clinic.schedule.portal.adapter.out.external;

import java.util.List;

import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.application.port.BookableReasonsPort;
import com.clinic.schedule.portal.domain.BookableReason;
import com.clinic.schedule.practitioner.api.PractitionerReader;

/** Answers {@link BookableReasonsPort} by asking the practitioner module through its api (D-05). */
@Component
class BookableReasonsAdapter implements BookableReasonsPort {

    private final PractitionerReader practitioners;

    BookableReasonsAdapter(PractitionerReader practitioners) {
        this.practitioners = practitioners;
    }

    @Override
    public List<BookableReason> all() {
        return practitioners.findAll().stream()
                .flatMap(practitioner -> practitioner.reasons().stream()
                        .map(reason -> new BookableReason(practitioner.id(), practitioner.fullName(), reason.id(),
                                reason.name(), reason.durationMinutes(), reason.webBookable())))
                .toList();
    }
}
