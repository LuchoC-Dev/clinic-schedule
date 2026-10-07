package com.clinic.schedule.cashdesk.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A day of the cash desk (D-87): the visits charged that day, in the order they were charged, what they
 * add up to, overall and by who recorded each, and the emergencies that came that day and are not charged
 * yet. In part 1 everything is cash, so what was charged is what should be in the drawer.
 *
 * @param charged the visits whose charge was recorded that day
 * @param pending the emergencies that came that day, not charged yet
 */
public record CashDeskDay(LocalDate date, List<Visit> charged, List<Visit> pending) {

    public CashDeskDay {
        if (date == null || charged == null || pending == null) {
            throw new IllegalArgumentException("A day of the cash desk needs its date, its charges and its pending visits");
        }
        if (charged.stream().anyMatch(Visit::pending) || pending.stream().anyMatch(visit -> !visit.pending())) {
            throw new IllegalArgumentException("The charged visits have their charge, and the pending ones do not");
        }
        charged = charged.stream()
                .sorted(Comparator.comparing((Visit visit) -> visit.charge().orElseThrow().recordedAt()))
                .toList();
        pending = pending.stream().sorted(Comparator.comparing(Visit::arrivedAt)).toList();
    }

    /** Everything charged that day. */
    public Total total() {
        return Total.of(charged);
    }

    /** What each user recorded, by username. */
    public List<RecorderTotal> byRecorder() {
        Map<String, List<Visit>> byUser = new TreeMap<>();
        for (Visit visit : charged) {
            byUser.computeIfAbsent(visit.charge().orElseThrow().recordedBy(), user -> new ArrayList<>()).add(visit);
        }
        return byUser.entrySet().stream()
                .map(entry -> new RecorderTotal(entry.getKey(), Total.of(entry.getValue())))
                .toList();
    }

    /** How many charges, and what they add up to. */
    public record Total(int count, BigDecimal amount) {

        static Total of(List<Visit> visits) {
            return new Total(visits.size(), visits.stream()
                    .map(visit -> visit.charge().orElseThrow().amount().value())
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        }
    }

    /** What one user recorded. */
    public record RecorderTotal(String recordedBy, Total total) {
    }
}
