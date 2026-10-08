package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Kind;
import com.clinic.schedule.cashdesk.domain.Coverage;
import com.clinic.schedule.cashdesk.domain.CoverageWithdrawal;
import com.clinic.schedule.cashdesk.domain.Means;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * A day of the cash desk as reception gets it (D-87, D-88): each charge and refund with its patient — a
 * refund with why, a charge with what is left of it, either saying whether it was a copay (D-90) —, the totals,
 * overall and by who recorded them, the emergencies of that day never charged nor covered, and the visits covered
 * that day, with the insurance as it was then and the withdrawal of a coverage marked by mistake.
 */
record CashDeskResponse(LocalDate date, List<Movement> movements, Totals.Total total,
        List<Totals.RecorderTotal> byRecorder, List<Pending> pending, List<Covered> covered) {

    /**
     * @param copay whether it was, or gave back from, the copay of a covered visit
     * @param left what is left of the charge now; null for a refund
     * @param reason why it was given back; null for a charge
     */
    record Movement(Kind kind, UUID visitId, Patient patient, boolean emergency, boolean copay, BigDecimal amount,
            Means means, BigDecimal left, String reason, String recordedBy, LocalDateTime recordedAt) {
    }

    /**
     * A visit covered that day.
     *
     * @param memberNumber null when the patient had none recorded
     * @param plan null when the patient had none recorded
     * @param token null when the insurer asked for none
     * @param withdrawal null unless the coverage was withdrawn
     */
    record Covered(UUID visitId, Patient patient, boolean emergency, String insurer, String memberNumber,
            String plan, String token, String recordedBy, LocalDateTime recordedAt, Withdrawal withdrawal) {
    }

    record Withdrawal(String reason, String recordedBy, LocalDateTime recordedAt) {
    }

    record Pending(UUID visitId, Patient patient, LocalDateTime arrivedAt) {
    }

    /** {@code document} is null when the patient gave none. */
    record Patient(UUID id, String fullName, String document) {
    }

    static CashDeskResponse of(CashDeskDay day, Map<UUID, PatientSummary> patients) {
        return new CashDeskResponse(day.date(),
                day.movements().stream().map(movement -> movement(movement, patients)).toList(),
                Totals.Total.of(day.total()), Totals.byRecorder(day),
                day.pending().stream()
                        .map(visit -> new Pending(visit.id(), patient(visit, patients), visit.arrivedAt()))
                        .toList(),
                day.covered().stream().map(visit -> covered(visit, patients)).toList());
    }

    private static Covered covered(Visit visit, Map<UUID, PatientSummary> patients) {
        Coverage coverage = visit.coverage().orElseThrow();
        CoverageWithdrawal withdrawal = coverage.withdrawal();
        return new Covered(visit.id(), patient(visit, patients), visit.emergency(), coverage.insurance().insurer(),
                coverage.insurance().memberNumber(), coverage.insurance().plan(), coverage.token(),
                coverage.recordedBy(), coverage.recordedAt(), withdrawal == null ? null
                        : new Withdrawal(withdrawal.reason(), withdrawal.recordedBy(), withdrawal.recordedAt()));
    }

    private static Movement movement(CashDeskDay.Movement movement, Map<UUID, PatientSummary> patients) {
        Visit visit = movement.visit();
        return new Movement(movement.kind(), visit.id(), patient(visit, patients), visit.emergency(),
                movement.copay(), movement.amount().value(), movement.means(),
                movement.kind() == Kind.CHARGE ? movement.charge().left() : null, movement.reason(),
                movement.recordedBy(), movement.recordedAt());
    }

    private static Patient patient(Visit visit, Map<UUID, PatientSummary> patients) {
        PatientSummary patient = patients.get(visit.patientId());
        return new Patient(visit.patientId(), patient == null ? null : patient.fullName(),
                patient == null ? null : patient.document());
    }
}
