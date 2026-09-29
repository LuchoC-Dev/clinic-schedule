package com.clinic.schedule.clinicalrecord.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.port.RecordReadingReader;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;

/**
 * The admins look through who read which clinical record (D-71): by patient, by practitioner and by
 * days, the most recent first. Only who, whose, when, by which access and whether it was a file —
 * never what was read. Who may do it is decided by route: the admins alone (D-34).
 *
 * <p>Without days, the last week. At most {@value #LIMIT} readings come back, and the answer says
 * when there were more, so a narrower search finds them.
 */
@Service
public class ListRecordReadingsUseCase {

    static final int LIMIT = 500;
    private static final int DEFAULT_DAYS = 7;

    private final RecordReadingReader readings;
    private final Clock clock;

    public ListRecordReadingsUseCase(RecordReadingReader readings, Clock clock) {
        this.readings = readings;
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
        List<RecordReading> found = readings.find(command.patients(), command.practitioner(), from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(), LIMIT + 1);
        return new Result(found.stream().limit(LIMIT).toList(), found.size() > LIMIT, from, to);
    }

    /**
     * @param patients only these patients' records; null for anyone's, empty for nobody's
     * @param practitioner only this practitioner's readings; null for anyone's
     * @param from the first day, included; null for a week before the last
     * @param to the last day, included; null for today
     */
    public record Command(Set<PatientId> patients, PractitionerId practitioner, LocalDate from, LocalDate to) {
    }

    /** The readings found, whether there were more than came back, and the days searched. */
    public record Result(List<RecordReading> readings, boolean limitReached, LocalDate from, LocalDate to) {
    }
}
