package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.Charge;
import com.clinic.schedule.cashdesk.domain.Means;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * A day of the cash desk as reception gets it (D-87): each charge with its patient, the totals, overall and
 * by who recorded them, and the emergencies of that day not charged yet.
 */
record CashDeskResponse(LocalDate date, List<Movement> movements, Total total, List<RecorderTotal> byRecorder,
        List<Pending> pending) {

    record Movement(UUID visitId, Patient patient, boolean emergency, BigDecimal amount, Means means,
            String recordedBy, LocalDateTime recordedAt) {
    }

    record Pending(UUID visitId, Patient patient, LocalDateTime arrivedAt) {
    }

    /** {@code document} is null when the patient gave none. */
    record Patient(UUID id, String fullName, String document) {
    }

    record Total(int count, BigDecimal amount) {
    }

    record RecorderTotal(String recordedBy, int count, BigDecimal amount) {
    }

    static CashDeskResponse of(CashDeskDay day, Map<UUID, PatientSummary> patients) {
        return new CashDeskResponse(day.date(),
                day.charged().stream().map(visit -> movement(visit, patients)).toList(),
                new Total(day.total().count(), day.total().amount()),
                day.byRecorder().stream()
                        .map(each -> new RecorderTotal(each.recordedBy(), each.total().count(), each.total().amount()))
                        .toList(),
                day.pending().stream()
                        .map(visit -> new Pending(visit.id(), patient(visit, patients), visit.arrivedAt()))
                        .toList());
    }

    private static Movement movement(Visit visit, Map<UUID, PatientSummary> patients) {
        Charge charge = visit.charge().orElseThrow();
        return new Movement(visit.id(), patient(visit, patients), visit.emergency(), charge.amount().value(),
                charge.means(), charge.recordedBy(), charge.recordedAt());
    }

    private static Patient patient(Visit visit, Map<UUID, PatientSummary> patients) {
        PatientSummary patient = patients.get(visit.patientId());
        return new Patient(visit.patientId(), patient == null ? null : patient.fullName(),
                patient == null ? null : patient.document());
    }
}
