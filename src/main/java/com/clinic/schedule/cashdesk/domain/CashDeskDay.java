package com.clinic.schedule.cashdesk.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A day of the cash desk (D-87, D-88): the charges and the refunds recorded that day, in the order they were
 * recorded, what they add up to — charged, given back, and what is left —, overall and by who recorded each,
 * and the emergencies that came that day and were never charged. A refund counts the day it is recorded,
 * whatever day its charge was. In part 1 everything is cash, so what is left is what should be in the drawer.
 *
 * @param visits the visits with a charge or a refund recorded that day, whole
 * @param pending the emergencies that came that day, never charged
 */
public record CashDeskDay(LocalDate date, List<Visit> visits, List<Visit> pending) {

    public CashDeskDay {
        if (date == null || visits == null || pending == null) {
            throw new IllegalArgumentException("A day of the cash desk needs its date, its visits and its pending ones");
        }
        if (pending.stream().anyMatch(visit -> !visit.pending())) {
            throw new IllegalArgumentException("A pending visit was never charged");
        }
        visits = List.copyOf(visits);
        pending = pending.stream().sorted(Comparator.comparing(Visit::arrivedAt)).toList();
    }

    /** What happened at the desk that day: each charge and refund recorded on it, in order. */
    public List<Movement> movements() {
        List<Movement> movements = new ArrayList<>();
        for (Visit visit : visits) {
            for (Charge charge : visit.charges()) {
                if (isToday(charge.recordedAt())) {
                    movements.add(Movement.of(visit, charge));
                }
                for (Refund refund : charge.refunds()) {
                    if (isToday(refund.recordedAt())) {
                        movements.add(Movement.of(visit, charge, refund));
                    }
                }
            }
        }
        movements.sort(Comparator.comparing(Movement::recordedAt));
        return movements;
    }

    /** Everything charged and given back that day. */
    public Total total() {
        return Total.of(movements());
    }

    /** What each user recorded, by username. */
    public List<RecorderTotal> byRecorder() {
        Map<String, List<Movement>> byUser = new TreeMap<>();
        for (Movement movement : movements()) {
            byUser.computeIfAbsent(movement.recordedBy(), user -> new ArrayList<>()).add(movement);
        }
        return byUser.entrySet().stream()
                .map(entry -> new RecorderTotal(entry.getKey(), Total.of(entry.getValue())))
                .toList();
    }

    private boolean isToday(LocalDateTime at) {
        return at.toLocalDate().equals(date);
    }

    /** A charge or a refund. */
    public enum Kind {
        CHARGE, REFUND
    }

    /**
     * Money that came in or went out, with its visit and its charge.
     *
     * @param reason why it was given back; null for a charge
     */
    public record Movement(Kind kind, Visit visit, Charge charge, Amount amount, String reason, String recordedBy,
            LocalDateTime recordedAt) {

        static Movement of(Visit visit, Charge charge) {
            return new Movement(Kind.CHARGE, visit, charge, charge.amount(), null, charge.recordedBy(),
                    charge.recordedAt());
        }

        static Movement of(Visit visit, Charge charge, Refund refund) {
            return new Movement(Kind.REFUND, visit, charge, refund.amount(), refund.reason(), refund.recordedBy(),
                    refund.recordedAt());
        }

        public UUID visitId() {
            return visit.id();
        }

        public Means means() {
            return charge.means();
        }
    }

    /** Some charges and refunds: how many of each, what each adds up to, and what is left. */
    public record Total(Sum charged, Sum refunded) {

        static Total of(List<Movement> movements) {
            return new Total(Sum.of(movements, Kind.CHARGE), Sum.of(movements, Kind.REFUND));
        }

        /** What was charged, less what was given back. */
        public BigDecimal left() {
            return charged.amount().subtract(refunded.amount());
        }
    }

    /** How many movements of a kind, and what they add up to. */
    public record Sum(int count, BigDecimal amount) {

        static Sum of(List<Movement> movements, Kind kind) {
            List<Movement> ofKind = movements.stream().filter(movement -> movement.kind() == kind).toList();
            return new Sum(ofKind.size(), ofKind.stream()
                    .map(movement -> movement.amount().value())
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        }
    }

    /** What one user recorded. */
    public record RecorderTotal(String recordedBy, Total total) {
    }
}
