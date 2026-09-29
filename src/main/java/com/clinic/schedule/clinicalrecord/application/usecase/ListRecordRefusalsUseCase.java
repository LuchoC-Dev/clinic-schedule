package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.port.RecordRefusalReader;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;

/**
 * The admins look through the refused attempts to reach clinical content (D-72): by patient, by user
 * and by days, the most recent first. Who may do it is decided by route: the admins alone (D-34).
 *
 * <p>As with the readings (D-71): without days, the last week; at most {@value #LIMIT} come back, and
 * the answer says when there were more.
 */
@Service
public class ListRecordRefusalsUseCase {

    static final int LIMIT = 500;
    private static final int DEFAULT_DAYS = 7;

    private final RecordRefusalReader refusals;
    private final Clock clock;

    public ListRecordRefusalsUseCase(RecordRefusalReader refusals, Clock clock) {
        this.refusals = refusals;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException when the first day comes after the last */
    @Transactional(readOnly = true)
    public Result handle(Command command) {
        LocalDate to = command.to() != null ? command.to() : LocalDate.now(clock);
        LocalDate from = command.from() != null ? command.from() : to.minusDays(DEFAULT_DAYS - 1);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The first day comes after the last");
        }
        String refusedBy = command.refusedBy() == null || command.refusedBy().isBlank() ? null
                : command.refusedBy().strip();
        List<RecordRefusal> found = refusals.find(command.patients(), refusedBy, from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(), LIMIT + 1);
        return new Result(found.stream().limit(LIMIT).toList(), found.size() > LIMIT, from, to);
    }

    /**
     * @param patients only those about these patients' records; null for any, empty for none
     * @param refusedBy only this user's; null or blank for anyone's
     * @param from the first day, included; null for a week before the last
     * @param to the last day, included; null for today
     */
    public record Command(Set<PatientId> patients, String refusedBy, LocalDate from, LocalDate to) {
    }

    /** The refusals found, whether there were more than came back, and the days searched. */
    public record Result(List<RecordRefusal> refusals, boolean limitReached, LocalDate from, LocalDate to) {
    }
}
